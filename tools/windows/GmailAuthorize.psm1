#Requires -Version 5.1
<#
  Gmail API authorization helper for Stock List Manager (functions). Run it through gmail-authorize.ps1.
  See docs/EMAIL_SETUP.md for the full procedure.

  What it does, in order. Any failed check stops it.
    1. SSH preflight, before any browser opens: the key is loaded in the OpenSSH agent, the server's host key in the
       helper's own known-hosts file equals the fingerprint the owner obtained independently, and the server's
       restricted command answers READY to "check".
    2. Authorization: Google's Desktop app loopback flow on 127.0.0.1 (random port) with PKCE (S256), state and nonce.
    3. Validation: granted scopes, refresh token present, and the ID token (signature against Google's published
       keys, issuer, audience, azp, expiry, issued-at, nonce, exact sender email, email_verified).
    4. Transfer: client id, client secret and refresh token go to the server only on the standard input of SSH, to the
       restricted command "update". Nothing is written to disk, shown, or put on a command line.
  If anything fails after Google issued the token, the token is revoked.

  The client secret of a Desktop OAuth client is not confidential (public client); PKCE protects the authorization
  code. The refresh token is the real secret.
#>

Set-StrictMode -Version 2.0

$script:GmailSendScope = 'https://www.googleapis.com/auth/gmail.send'
$script:IdentityScopes = @('openid', 'email', 'https://www.googleapis.com/auth/userinfo.email')
$script:AuthEndpoint = 'https://accounts.google.com/o/oauth2/v2/auth'
$script:TokenEndpoint = 'https://oauth2.googleapis.com/token'
$script:RevokeEndpoint = 'https://oauth2.googleapis.com/revoke'
$script:JwksEndpoint = 'https://www.googleapis.com/oauth2/v3/certs'
$script:Issuers = @('https://accounts.google.com', 'accounts.google.com')
$script:UpdatedLine = 'UPDATED GMAIL_OAUTH_CLIENT_ID GMAIL_OAUTH_CLIENT_SECRET GMAIL_OAUTH_REFRESH_TOKEN'

# ------------------------------------------------------------------------------------------------ encoding helpers

function ConvertTo-Base64Url {
    param([Parameter(Mandatory)][byte[]]$Bytes)
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function ConvertFrom-Base64Url {
    param([Parameter(Mandatory)][string]$Text)
    $s = $Text.Replace('-', '+').Replace('_', '/')
    switch ($s.Length % 4) {
        2 { $s += '==' }
        3 { $s += '=' }
        1 { throw 'Invalid base64url text' }
    }
    return [Convert]::FromBase64String($s)
}

function New-RandomBase64Url {
    param([int]$ByteCount = 32)
    $bytes = New-Object byte[] $ByteCount
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ConvertTo-Base64Url $bytes
}

function New-PkcePair {
    $verifier = New-RandomBase64Url -ByteCount 64
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { $hash = $sha.ComputeHash([System.Text.Encoding]::ASCII.GetBytes($verifier)) } finally { $sha.Dispose() }
    return [pscustomobject]@{ Verifier = $verifier; Challenge = (ConvertTo-Base64Url $hash) }
}

function Test-SameText {
    # Constant-time comparison (for state).
    param([string]$A, [string]$B)
    if ($null -eq $A -or $null -eq $B) { return $false }
    $x = [System.Text.Encoding]::UTF8.GetBytes($A)
    $y = [System.Text.Encoding]::UTF8.GetBytes($B)
    $diff = $x.Length -bxor $y.Length
    for ($i = 0; $i -lt [Math]::Max($x.Length, $y.Length); $i++) {
        $p = if ($i -lt $x.Length) { $x[$i] } else { 0 }
        $q = if ($i -lt $y.Length) { $y[$i] } else { 0 }
        $diff = $diff -bor ($p -bxor $q)
    }
    return ($diff -eq 0)
}

function Get-QueryParameters {
    param([string]$Query)
    $result = @{}
    if ([string]::IsNullOrEmpty($Query)) { return $result }
    foreach ($pair in $Query.TrimStart('?').Split('&')) {
        if ($pair -eq '') { continue }
        $kv = $pair.Split('=', 2)
        $name = [Uri]::UnescapeDataString($kv[0].Replace('+', ' '))
        $value = if ($kv.Length -gt 1) { [Uri]::UnescapeDataString($kv[1].Replace('+', ' ')) } else { '' }
        if (-not $result.ContainsKey($name)) { $result[$name] = $value }
    }
    return $result
}

function ConvertTo-PlainText {
    param([Parameter(Mandatory)][System.Security.SecureString]$Secure)
    return (New-Object System.Net.NetworkCredential('', $Secure)).Password
}

# ------------------------------------------------------------------------------------------------ SSH

function Get-SshHost {
    param([Parameter(Mandatory)][string]$Target)
    if ($Target -notmatch '^[A-Za-z0-9._-]+@([A-Za-z0-9.-]+)$') {
        throw 'SshTarget must look like deploy@SERVER (a host name or IPv4 address).'
    }
    return $Matches[1]
}

function Get-SshArguments {
    param(
        [Parameter(Mandatory)][string]$Target,
        [Parameter(Mandatory)][string]$KeyPath,
        [Parameter(Mandatory)][string]$KnownHostsFile,
        [Parameter(Mandatory)][ValidateSet('check', 'update')][string]$Operation
    )
    return @(
        '-T',
        '-o', 'BatchMode=yes',
        '-o', 'IdentitiesOnly=yes',
        '-o', 'StrictHostKeyChecking=yes',
        '-o', ('UserKnownHostsFile="' + $KnownHostsFile + '"'),
        '-o', 'GlobalKnownHostsFile=none',
        '-o', 'ConnectTimeout=20',
        '-o', 'ServerAliveInterval=15',
        '-i', $KeyPath,
        $Target,
        $Operation
    )
}

function Get-KnownHostFingerprints {
    param([Parameter(Mandatory)][string]$HostName, [Parameter(Mandatory)][string]$KnownHostsFile)
    if (-not (Test-Path -LiteralPath $KnownHostsFile -PathType Leaf)) { return @() }
    $lines = & ssh-keygen -l -F $HostName -f $KnownHostsFile 2>$null
    $prints = @()
    foreach ($line in @($lines)) {
        if ($line -match '(SHA256:[A-Za-z0-9+/]+=*)') { $prints += $Matches[1] }
    }
    return $prints
}

function Assert-HostKeyPinned {
    # Every key stored for the host in the helper's own known-hosts file must be the fingerprint the owner obtained
    # independently (EC2 system log or an AWS console session). No key is ever trusted on first use.
    param(
        [Parameter(Mandatory)][string]$HostName,
        [Parameter(Mandatory)][string]$KnownHostsFile,
        [Parameter(Mandatory)][string]$Expected
    )
    if ($Expected -notmatch '^SHA256:[A-Za-z0-9+/]+=*$') {
        throw 'ExpectedHostKey must be a SHA256 fingerprint such as SHA256:AbC... (see docs/EMAIL_SETUP.md).'
    }
    $prints = @(Get-KnownHostFingerprints -HostName $HostName -KnownHostsFile $KnownHostsFile)
    if ($prints.Count -eq 0) {
        throw "The server's host key is not in $KnownHostsFile. Add it after checking its fingerprint (docs/EMAIL_SETUP.md)."
    }
    foreach ($p in $prints) {
        if (-not [string]::Equals($p.TrimEnd('='), $Expected.TrimEnd('='), [StringComparison]::Ordinal)) {
            throw "The server's host key in $KnownHostsFile does not match ExpectedHostKey. Stop and check the server (docs/EMAIL_SETUP.md)."
        }
    }
}

function Assert-AgentHasKey {
    param([Parameter(Mandatory)][string]$KeyPath)
    $pubPath = if ($KeyPath.EndsWith('.pub')) { $KeyPath } else { "$KeyPath.pub" }
    if (-not (Test-Path -LiteralPath $pubPath -PathType Leaf)) {
        throw "The public key file $pubPath is missing."
    }
    $pub = (Get-Content -LiteralPath $pubPath -Raw).Trim().Split(' ')
    if ($pub.Length -lt 2) { throw "The public key file $pubPath is not an OpenSSH public key." }
    $loaded = & ssh-add -L 2>$null
    foreach ($line in @($loaded)) {
        $parts = "$line".Trim().Split(' ')
        if ($parts.Length -ge 2 -and $parts[0] -eq $pub[0] -and $parts[1] -eq $pub[1]) { return }
    }
    throw 'The SSH key is not loaded in the OpenSSH Authentication Agent. Run: ssh-add <your key>, then try again.'
}

function Invoke-Ssh {
    # Runs ssh with the given arguments. InputLines (if any) are written to its standard input, which is then closed.
    param([Parameter(Mandatory)][string[]]$Arguments, [string[]]$InputLines = @())
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'ssh'
    $quoted = foreach ($a in $Arguments) { if ($a -match '[\s"]') { '"' + $a.Replace('"', '\"') + '"' } else { $a } }
    $psi.Arguments = ($quoted -join ' ')
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $process = [System.Diagnostics.Process]::Start($psi)
    try {
        $outTask = $process.StandardOutput.ReadToEndAsync()
        $errTask = $process.StandardError.ReadToEndAsync()
        $stdin = $process.StandardInput
        $stdin.NewLine = "`n"
        foreach ($l in $InputLines) { $stdin.WriteLine($l) }
        $stdin.Close()
        if (-not $process.WaitForExit(120000)) {
            $process.Kill()
            throw 'SSH did not finish within 2 minutes.'
        }
        $process.WaitForExit()
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            StdOut   = $outTask.Result.Trim()
            StdErr   = $errTask.Result.Trim()
        }
    } finally {
        $process.Dispose()
    }
}

# ------------------------------------------------------------------------------------------------ OAuth

function Get-AuthorizationUrl {
    param(
        [Parameter(Mandatory)][string]$ClientId,
        [Parameter(Mandatory)][string]$RedirectUri,
        [Parameter(Mandatory)][string]$State,
        [Parameter(Mandatory)][string]$Nonce,
        [Parameter(Mandatory)][string]$CodeChallenge,
        [Parameter(Mandatory)][string]$LoginHint
    )
    $query = [ordered]@{
        client_id             = $ClientId
        redirect_uri          = $RedirectUri
        response_type         = 'code'
        scope                 = "openid email $script:GmailSendScope"
        access_type           = 'offline'
        prompt                = 'consent'
        login_hint            = $LoginHint
        state                 = $State
        nonce                 = $Nonce
        code_challenge        = $CodeChallenge
        code_challenge_method = 'S256'
    }
    $parts = foreach ($k in $query.Keys) { "$k=" + [Uri]::EscapeDataString([string]$query[$k]) }
    return $script:AuthEndpoint + '?' + ($parts -join '&')
}

function Start-LoopbackListener {
    $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    return $listener
}

function Send-HttpReply {
    param($Stream, [int]$Status, [string]$Text)
    $reason = if ($Status -eq 200) { 'OK' } else { 'Not Found' }
    $html = "<!DOCTYPE html><html><head><meta charset=`"utf-8`"><title>Stock List Manager</title></head><body style=`"font-family:sans-serif`"><p>$Text</p></body></html>"
    $body = [System.Text.Encoding]::UTF8.GetBytes($html)
    $head = "HTTP/1.1 $Status $reason`r`nContent-Type: text/html; charset=utf-8`r`nContent-Length: $($body.Length)`r`nCache-Control: no-store`r`nConnection: close`r`n`r`n"
    $headBytes = [System.Text.Encoding]::ASCII.GetBytes($head)
    $Stream.Write($headBytes, 0, $headBytes.Length)
    $Stream.Write($body, 0, $body.Length)
    $Stream.Flush()
}

function Wait-AuthorizationCode {
    # Waits for Google's redirect on the loopback listener. Requests without a state parameter get 404 and are
    # ignored; the first request that carries state ends the wait, whatever its result.
    param(
        [Parameter(Mandatory)]$Listener,
        [Parameter(Mandatory)][string]$State,
        [int]$TimeoutSeconds = 300
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ($true) {
        $remaining = [int]($deadline - [DateTime]::UtcNow).TotalMilliseconds
        if ($remaining -le 0) { throw 'No answer from Google within 5 minutes.' }
        $task = $Listener.AcceptTcpClientAsync()
        if (-not $task.Wait($remaining)) { throw 'No answer from Google within 5 minutes.' }
        $client = $task.Result
        try {
            $client.ReceiveTimeout = 10000
            $stream = $client.GetStream()
            $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::ASCII, $false, 8192, $true)
            $requestLine = $reader.ReadLine()
            while ($true) {
                $h = $reader.ReadLine()
                if ($null -eq $h -or $h -eq '') { break }
            }
            $target = ''
            if ($requestLine -match '^GET (\S+) HTTP/1\.[01]$') { $target = $Matches[1] }
            $query = ''
            $q = $target.IndexOf('?')
            if ($q -ge 0) { $query = $target.Substring($q + 1) }
            $params = Get-QueryParameters $query
            if (-not $params.ContainsKey('state')) {
                Send-HttpReply $stream 404 'Not found.'
                continue
            }
            if (-not (Test-SameText $params['state'] $State)) {
                Send-HttpReply $stream 200 'The answer did not match this sign-in. Nothing was saved. You can close this window.'
                throw 'The state value did not match. Nothing was saved; start again.'
            }
            if ($params.ContainsKey('error')) {
                Send-HttpReply $stream 200 'Sign-in was cancelled or refused. Nothing was saved. You can close this window.'
                throw "Google answered with an error ($($params['error'] -replace '[^a-z_]', '')). Nothing was saved."
            }
            if (-not $params.ContainsKey('code') -or [string]::IsNullOrEmpty($params['code'])) {
                Send-HttpReply $stream 200 'No authorization code was received. You can close this window.'
                throw 'Google sent no authorization code.'
            }
            Send-HttpReply $stream 200 'Done. You can close this window and go back to PowerShell.'
            return [pscustomobject]@{ Code = $params['code']; Scope = $params['scope'] }
        } finally {
            $client.Close()
        }
    }
}

function Invoke-TokenExchange {
    param(
        [Parameter(Mandatory)][string]$Code,
        [Parameter(Mandatory)][string]$Verifier,
        [Parameter(Mandatory)][string]$ClientId,
        [Parameter(Mandatory)][string]$ClientSecret,
        [Parameter(Mandatory)][string]$RedirectUri
    )
    $body = @{
        grant_type    = 'authorization_code'
        code          = $Code
        code_verifier = $Verifier
        client_id     = $ClientId
        client_secret = $ClientSecret
        redirect_uri  = $RedirectUri
    }
    try {
        return Invoke-RestMethod -Method Post -Uri $script:TokenEndpoint -Body $body -ContentType 'application/x-www-form-urlencoded' -UseBasicParsing
    } catch {
        throw 'Google did not issue a token (the code exchange failed). Nothing was saved; start again.'
    }
}

function Get-GoogleJwks {
    try {
        return Invoke-RestMethod -Method Get -Uri $script:JwksEndpoint -UseBasicParsing
    } catch {
        throw "Could not download Google's signing keys. Nothing was saved."
    }
}

function Invoke-TokenRevocation {
    param([string]$Token)
    if ([string]::IsNullOrEmpty($Token)) { return }
    try {
        Invoke-RestMethod -Method Post -Uri $script:RevokeEndpoint -Body @{ token = $Token } -ContentType 'application/x-www-form-urlencoded' -UseBasicParsing | Out-Null
        Write-Host 'The new token was revoked at Google.'
    } catch {
        Write-Warning 'The new token could not be revoked automatically. Remove the app at myaccount.google.com > Security > Third-party connections.'
    }
}

# ------------------------------------------------------------------------------------------------ validation

function Assert-GrantedScopes {
    # gmail.send must be granted. openid and email (also returned as userinfo.email) are accepted for identity.
    # Any other scope means the grant is not what was asked for.
    param([string]$Scope)
    $granted = @()
    if (-not [string]::IsNullOrWhiteSpace($Scope)) {
        $granted = @($Scope.Split(' ', [StringSplitOptions]::RemoveEmptyEntries))
    }
    if ($granted -notcontains $script:GmailSendScope) {
        throw 'The Gmail send permission was not granted. Start again and allow "Send email on your behalf".'
    }
    foreach ($s in $granted) {
        if ($s -ne $script:GmailSendScope -and $script:IdentityScopes -notcontains $s) {
            throw 'Google granted a permission that was not requested. Nothing was saved.'
        }
    }
}

function Get-JsonPart {
    param([string]$Part)
    $json = [System.Text.Encoding]::UTF8.GetString((ConvertFrom-Base64Url $Part))
    return ($json | ConvertFrom-Json)
}

function Get-Claim {
    param($Object, [string]$Name)
    $p = $Object.PSObject.Properties[$Name]
    if ($null -eq $p) { return $null }
    return $p.Value
}

function Assert-IdToken {
    # Full validation of the ID token from the token response.
    param(
        [Parameter(Mandatory)][string]$IdToken,
        [Parameter(Mandatory)]$Jwks,
        [Parameter(Mandatory)][string]$ClientId,
        [Parameter(Mandatory)][string]$Nonce,
        [Parameter(Mandatory)][string]$Sender,
        [DateTimeOffset]$Now = [DateTimeOffset]::UtcNow
    )
    $parts = $IdToken.Split('.')
    if ($parts.Length -ne 3) { throw 'ID token check failed: malformed token.' }
    try {
        $header = Get-JsonPart $parts[0]
        $claims = Get-JsonPart $parts[1]
        $signature = ConvertFrom-Base64Url $parts[2]
    } catch {
        throw 'ID token check failed: malformed token.'
    }

    if ((Get-Claim $header 'alg') -cne 'RS256') { throw 'ID token check failed: algorithm is not RS256.' }
    $kid = Get-Claim $header 'kid'
    if ([string]::IsNullOrEmpty($kid)) { throw 'ID token check failed: no key id.' }
    $key = $null
    foreach ($k in @(Get-Claim $Jwks 'keys')) {
        if ((Get-Claim $k 'kid') -ceq $kid -and (Get-Claim $k 'kty') -ceq 'RSA') { $key = $k }
    }
    if ($null -eq $key) { throw "ID token check failed: unknown signing key." }

    $rsa = [System.Security.Cryptography.RSA]::Create()
    try {
        $parameters = New-Object System.Security.Cryptography.RSAParameters
        $parameters.Modulus = ConvertFrom-Base64Url (Get-Claim $key 'n')
        $parameters.Exponent = ConvertFrom-Base64Url (Get-Claim $key 'e')
        $rsa.ImportParameters($parameters)
        $signed = [System.Text.Encoding]::ASCII.GetBytes($parts[0] + '.' + $parts[1])
        $valid = $rsa.VerifyData($signed, $signature, [System.Security.Cryptography.HashAlgorithmName]::SHA256,
            [System.Security.Cryptography.RSASignaturePadding]::Pkcs1)
    } finally {
        $rsa.Dispose()
    }
    if (-not $valid) { throw 'ID token check failed: bad signature.' }

    if ($script:Issuers -cnotcontains [string](Get-Claim $claims 'iss')) { throw 'ID token check failed: wrong issuer.' }
    $aud = @(Get-Claim $claims 'aud')
    if ($aud.Count -ne 1 -or [string]$aud[0] -cne $ClientId) { throw 'ID token check failed: wrong audience.' }
    $azp = Get-Claim $claims 'azp'
    if ($null -ne $azp -and [string]$azp -cne $ClientId) { throw 'ID token check failed: wrong authorized party.' }

    $nowSeconds = $Now.ToUnixTimeSeconds()
    $exp = Get-Claim $claims 'exp'
    $iat = Get-Claim $claims 'iat'
    if ($null -eq $exp -or [long]$exp -le $nowSeconds) { throw 'ID token check failed: expired.' }
    if ($null -eq $iat -or [long]$iat -gt ($nowSeconds + 300)) { throw 'ID token check failed: issued in the future.' }

    if (-not (Test-SameText ([string](Get-Claim $claims 'nonce')) $Nonce)) { throw 'ID token check failed: wrong nonce.' }

    $email = [string](Get-Claim $claims 'email')
    if (-not [string]::Equals($email, $Sender, [StringComparison]::OrdinalIgnoreCase)) {
        throw "ID token check failed: you signed in with a different Google account. Sign in as $Sender."
    }
    $verified = Get-Claim $claims 'email_verified'
    if (-not ($verified -eq $true -or [string]$verified -ceq 'true')) {
        throw 'ID token check failed: the email address is not verified.'
    }
}

# ------------------------------------------------------------------------------------------------ the whole flow

function Read-ClientCredentials {
    $id = (Read-Host 'OAuth client ID (from Google Cloud console)').Trim()
    $secure = Read-Host 'OAuth client secret (not shown)' -AsSecureString
    return [pscustomobject]@{ ClientId = $id; ClientSecret = (ConvertTo-PlainText $secure).Trim() }
}

function Open-Browser {
    param([Parameter(Mandatory)][string]$Url)
    Start-Process $Url
}

function Invoke-GmailAuthorization {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$SshTarget,
        [Parameter(Mandatory)][string]$SshKey,
        [Parameter(Mandatory)][string]$ExpectedHostKey,
        [string]$Sender = 'suppliermanagement.mail@gmail.com',
        [string]$KnownHostsFile = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.ssh\stocklist_known_hosts')
    )
    $ErrorActionPreference = 'Stop'
    try {
        # Windows PowerShell 5.1 may still default to old TLS versions; Google needs TLS 1.2 or newer.
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    } catch {
    }

    # 1. SSH preflight. Nothing is asked from Google until the exact update path is shown to work.
    Write-Host 'Step 1 of 4: checking the SSH connection to the server...'
    $sshHost = Get-SshHost $SshTarget
    Assert-AgentHasKey -KeyPath $SshKey
    Assert-HostKeyPinned -HostName $sshHost -KnownHostsFile $KnownHostsFile -Expected $ExpectedHostKey
    $check = Invoke-Ssh -Arguments (Get-SshArguments -Target $SshTarget -KeyPath $SshKey -KnownHostsFile $KnownHostsFile -Operation 'check')
    if ($check.ExitCode -ne 0 -or $check.StdOut -cne 'READY') {
        throw "The server is not ready for the token update (ssh exit code $($check.ExitCode)). Fix SSH access first; see docs/EMAIL_SETUP.md."
    }
    Write-Host 'SSH preflight passed: the server answered READY.'

    $credentials = $null
    $listener = $null
    $tokens = $null
    $delivered = $false
    try {
        # 2. Authorization with PKCE, state and nonce on a loopback-only listener.
        Write-Host 'Step 2 of 4: sign in with Google in the browser...'
        $credentials = Read-ClientCredentials
        if ($credentials.ClientId -notmatch '^[0-9]+-[A-Za-z0-9_]+\.apps\.googleusercontent\.com$') {
            throw 'The client ID does not look like a Google OAuth client ID.'
        }
        if ([string]::IsNullOrEmpty($credentials.ClientSecret)) { throw 'The client secret is empty.' }
        $state = New-RandomBase64Url -ByteCount 32
        $nonce = New-RandomBase64Url -ByteCount 32
        $pkce = New-PkcePair
        $listener = Start-LoopbackListener
        $redirectUri = "http://127.0.0.1:$($listener.LocalEndpoint.Port)/"
        Open-Browser (Get-AuthorizationUrl -ClientId $credentials.ClientId -RedirectUri $redirectUri -State $state -Nonce $nonce -CodeChallenge $pkce.Challenge -LoginHint $Sender)
        $answer = Wait-AuthorizationCode -Listener $listener -State $state
        $listener.Stop()
        $listener = $null

        $tokens = Invoke-TokenExchange -Code $answer.Code -Verifier $pkce.Verifier -ClientId $credentials.ClientId -ClientSecret $credentials.ClientSecret -RedirectUri $redirectUri

        # 3. Validation.
        Write-Host 'Step 3 of 4: checking what Google returned...'
        $refresh = [string](Get-Claim $tokens 'refresh_token')
        if ([string]::IsNullOrEmpty($refresh)) { throw 'Google returned no refresh token. Nothing was saved.' }
        Assert-GrantedScopes -Scope ([string](Get-Claim $tokens 'scope'))
        $idToken = [string](Get-Claim $tokens 'id_token')
        if ([string]::IsNullOrEmpty($idToken)) { throw 'Google returned no ID token. Nothing was saved.' }
        Assert-IdToken -IdToken $idToken -Jwks (Get-GoogleJwks) -ClientId $credentials.ClientId -Nonce $nonce -Sender $Sender

        # 4. Transfer over SSH standard input only.
        Write-Host 'Step 4 of 4: sending the token to the server over SSH...'
        $update = Invoke-Ssh -Arguments (Get-SshArguments -Target $SshTarget -KeyPath $SshKey -KnownHostsFile $KnownHostsFile -Operation 'update') `
            -InputLines @($credentials.ClientId, $credentials.ClientSecret, $refresh)
        if ($update.ExitCode -ne 0 -or $update.StdOut -cne $script:UpdatedLine) {
            throw "The server did not confirm the update (ssh exit code $($update.ExitCode)). Nothing was changed by this run."
        }
        $delivered = $true
        Write-Host 'Done. The server has the new token.'
        Write-Host 'Next (AWS console session): recreate the backend and send a test email, see docs/EMAIL_SETUP.md.'
    } catch {
        if (-not $delivered -and $null -ne $tokens) {
            $issued = [string](Get-Claim $tokens 'refresh_token')
            if ([string]::IsNullOrEmpty($issued)) { $issued = [string](Get-Claim $tokens 'access_token') }
            Invoke-TokenRevocation -Token $issued
            $issued = $null
        }
        throw
    } finally {
        if ($null -ne $listener) { $listener.Stop() }
        $credentials = $null
        $tokens = $null
        $refresh = $null
        [GC]::Collect()
    }
}

Export-ModuleMember -Function *
