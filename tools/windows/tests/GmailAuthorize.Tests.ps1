# Pester 5 tests for the Gmail authorization helper. Run: Invoke-Pester tools/windows/tests
# Nothing here calls Google or a real server: Google's answers are built with a test RSA key, and SSH is mocked
# (except the host-key check, which uses ssh-keygen on a throw-away known-hosts file).

BeforeAll {
    Import-Module (Join-Path $PSScriptRoot '..' 'GmailAuthorize.psm1') -Force

    $script:ClientId = '123456789012-abcdefghijklmnopqrstuvwxyz012345.apps.googleusercontent.com'
    $script:Sender = 'suppliermanagement.mail@gmail.com'
    $script:Nonce = 'test-nonce-value'
    $script:Now = [DateTimeOffset]::FromUnixTimeSeconds(1795000000)
    $script:GmailSend = 'https://www.googleapis.com/auth/gmail.send'

    $script:Rsa = [System.Security.Cryptography.RSA]::Create(2048)
    $script:OtherRsa = [System.Security.Cryptography.RSA]::Create(2048)
    $pub = $script:Rsa.ExportParameters($false)
    $script:Jwks = [pscustomobject]@{
        keys = @([pscustomobject]@{
                kid = 'test-key'; kty = 'RSA'; alg = 'RS256'; use = 'sig'
                n = (ConvertTo-Base64Url $pub.Modulus); e = (ConvertTo-Base64Url $pub.Exponent)
            })
    }

    function script:Get-GoodClaims {
        [ordered]@{
            iss            = 'https://accounts.google.com'
            azp            = $script:ClientId
            aud            = $script:ClientId
            sub            = '1234567890'
            email          = $script:Sender
            email_verified = $true
            nonce          = $script:Nonce
            iat            = $script:Now.ToUnixTimeSeconds() - 10
            exp            = $script:Now.ToUnixTimeSeconds() + 3000
        }
    }

    function script:New-TestIdToken {
        param($Claims = (Get-GoodClaims), $Header = [ordered]@{ alg = 'RS256'; kid = 'test-key'; typ = 'JWT' },
            [System.Security.Cryptography.RSA]$Key = $script:Rsa, [string]$Signature)
        $h = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes(($Header | ConvertTo-Json -Compress)))
        $c = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes(($Claims | ConvertTo-Json -Compress)))
        if (-not $Signature) {
            $sig = $Key.SignData([System.Text.Encoding]::ASCII.GetBytes("$h.$c"),
                [System.Security.Cryptography.HashAlgorithmName]::SHA256,
                [System.Security.Cryptography.RSASignaturePadding]::Pkcs1)
            $Signature = ConvertTo-Base64Url $sig
        }
        return "$h.$c.$Signature"
    }

    function script:Test-Token {
        param([string]$Token)
        Assert-IdToken -IdToken $Token -Jwks $script:Jwks -ClientId $script:ClientId -Nonce $script:Nonce `
            -Sender $script:Sender -Now $script:Now
    }
}

Describe 'ID token validation' {
    It 'accepts a valid token' {
        { Test-Token (New-TestIdToken) } | Should -Not -Throw
    }

    It 'accepts the short issuer form and the sender address in another case' {
        $c = Get-GoodClaims
        $c.iss = 'accounts.google.com'
        $c.email = 'SupplierManagement.Mail@Gmail.com'
        { Test-Token (New-TestIdToken -Claims $c) } | Should -Not -Throw
    }

    It 'rejects a token signed with another key' {
        { Test-Token (New-TestIdToken -Key $script:OtherRsa) } | Should -Throw '*bad signature*'
    }

    It 'rejects a token whose claims were changed after signing' {
        $parts = (New-TestIdToken).Split('.')
        $c = Get-GoodClaims
        $c.email = 'someone.else@gmail.com'
        $parts[1] = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes(($c | ConvertTo-Json -Compress)))
        { Test-Token ($parts -join '.') } | Should -Throw '*bad signature*'
    }

    It 'rejects an unknown key id' {
        { Test-Token (New-TestIdToken -Header ([ordered]@{ alg = 'RS256'; kid = 'other-key' })) } | Should -Throw '*unknown signing key*'
    }

    It 'rejects algorithm <alg>' -TestCases @(@{ alg = 'none' }, @{ alg = 'HS256' }, @{ alg = 'RS384' }, @{ alg = 'rs256' }) {
        param($alg)
        { Test-Token (New-TestIdToken -Header ([ordered]@{ alg = $alg; kid = 'test-key' })) } | Should -Throw '*not RS256*'
    }

    It 'rejects a malformed token' {
        { Test-Token 'abc.def' } | Should -Throw '*malformed*'
        { Test-Token 'abc.%%%.ghi' } | Should -Throw '*malformed*'
    }

    It 'rejects <name>' -TestCases @(
        @{ name = 'a wrong issuer'; field = 'iss'; value = 'https://evil.example'; message = '*wrong issuer*' }
        @{ name = 'a wrong audience'; field = 'aud'; value = 'other-client.apps.googleusercontent.com'; message = '*wrong audience*' }
        @{ name = 'two audiences'; field = 'aud'; value = @('123456789012-abcdefghijklmnopqrstuvwxyz012345.apps.googleusercontent.com', 'x'); message = '*wrong audience*' }
        @{ name = 'a wrong authorized party'; field = 'azp'; value = 'other-client.apps.googleusercontent.com'; message = '*wrong authorized party*' }
        @{ name = 'an expired token'; field = 'exp'; value = 1795000000 - 1; message = '*expired*' }
        @{ name = 'a token issued too far ahead'; field = 'iat'; value = 1795000000 + 301; message = '*future*' }
        @{ name = 'a wrong nonce'; field = 'nonce'; value = 'another-nonce'; message = '*wrong nonce*' }
        @{ name = 'another Google account'; field = 'email'; value = 'someone.else@gmail.com'; message = '*different Google account*' }
        @{ name = 'a look-alike account'; field = 'email'; value = 'suppliermanagement.mail@gmail.co'; message = '*different Google account*' }
        @{ name = 'an unverified email'; field = 'email_verified'; value = $false; message = '*not verified*' }
        @{ name = 'an unverified email (text)'; field = 'email_verified'; value = 'false'; message = '*not verified*' }
    ) {
        param($name, $field, $value, $message)
        $c = Get-GoodClaims
        $c[$field] = $value
        { Test-Token (New-TestIdToken -Claims $c) } | Should -Throw $message
    }

    It 'rejects a token without <field>' -TestCases @(
        @{ field = 'nonce'; message = '*wrong nonce*' }
        @{ field = 'email'; message = '*different Google account*' }
        @{ field = 'email_verified'; message = '*not verified*' }
        @{ field = 'exp'; message = '*expired*' }
        @{ field = 'iat'; message = '*future*' }
        @{ field = 'aud'; message = '*wrong audience*' }
    ) {
        param($field, $message)
        $c = Get-GoodClaims
        $c.Remove($field)
        { Test-Token (New-TestIdToken -Claims $c) } | Should -Throw $message
    }
}

Describe 'Granted scopes' {
    It 'accepts gmail.send with the identity scopes in any form' -TestCases @(
        @{ scope = "openid https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/gmail.send" }
        @{ scope = "https://www.googleapis.com/auth/gmail.send openid email" }
        @{ scope = "https://www.googleapis.com/auth/gmail.send" }
    ) {
        param($scope)
        { Assert-GrantedScopes -Scope $scope } | Should -Not -Throw
    }

    It 'rejects a grant without gmail.send' -TestCases @(
        @{ scope = 'openid email' }
        @{ scope = '' }
        @{ scope = 'https://www.googleapis.com/auth/gmail.readonly openid email' }
    ) {
        param($scope)
        { Assert-GrantedScopes -Scope $scope } | Should -Throw '*was not granted*'
    }

    It 'rejects any extra API scope' {
        { Assert-GrantedScopes -Scope "openid email $script:GmailSend https://www.googleapis.com/auth/drive.readonly" } |
            Should -Throw '*not requested*'
    }
}

Describe 'PKCE, state and the authorization address' {
    It 'derives the S256 challenge from the verifier' {
        $p = New-PkcePair
        $p.Verifier.Length | Should -Be 86
        $sha = [System.Security.Cryptography.SHA256]::Create()
        $expected = ConvertTo-Base64Url ($sha.ComputeHash([System.Text.Encoding]::ASCII.GetBytes($p.Verifier)))
        $p.Challenge | Should -BeExactly $expected
    }

    It 'makes different random values each time' {
        (New-RandomBase64Url) | Should -Not -Be (New-RandomBase64Url)
    }

    It 'compares state values exactly' {
        Test-SameText 'abc' 'abc' | Should -BeTrue
        Test-SameText 'abc' 'abd' | Should -BeFalse
        Test-SameText 'abc' 'abcd' | Should -BeFalse
        Test-SameText 'abc' $null | Should -BeFalse
    }

    It 'asks for exactly openid, email and gmail.send with PKCE, state, nonce and offline access' {
        $url = Get-AuthorizationUrl -ClientId $script:ClientId -RedirectUri 'http://127.0.0.1:50000/' -State 'S1' `
            -Nonce 'N1' -CodeChallenge 'C1' -LoginHint $script:Sender
        $url | Should -BeLike 'https://accounts.google.com/o/oauth2/v2/auth?*'
        $q = Get-QueryParameters ($url.Split('?')[1])
        $q['scope'] | Should -BeExactly "openid email $script:GmailSend"
        $q['code_challenge_method'] | Should -BeExactly 'S256'
        $q['code_challenge'] | Should -BeExactly 'C1'
        $q['state'] | Should -BeExactly 'S1'
        $q['nonce'] | Should -BeExactly 'N1'
        $q['access_type'] | Should -BeExactly 'offline'
        $q['prompt'] | Should -BeExactly 'consent'
        $q['redirect_uri'] | Should -BeExactly 'http://127.0.0.1:50000/'
        $q['login_hint'] | Should -BeExactly $script:Sender
        $q.ContainsKey('include_granted_scopes') | Should -BeFalse
    }
}

Describe 'Loopback listener' {
    BeforeAll {
        function script:Start-Client {
            param([int]$Port, [string[]]$Paths)
            $ps = [powershell]::Create()
            [void]$ps.AddScript({
                    param($Port, $Paths)
                    $statuses = @()
                    foreach ($path in $Paths) {
                        $client = New-Object System.Net.Sockets.TcpClient('127.0.0.1', $Port)
                        $stream = $client.GetStream()
                        $req = [System.Text.Encoding]::ASCII.GetBytes("GET $path HTTP/1.1`r`nHost: 127.0.0.1`r`n`r`n")
                        $stream.Write($req, 0, $req.Length)
                        $reader = New-Object System.IO.StreamReader($stream)
                        $statuses += $reader.ReadLine()
                        $client.Close()
                    }
                    $statuses
                }).AddArgument($Port).AddArgument($Paths)
            return [pscustomobject]@{ Shell = $ps; Handle = $ps.BeginInvoke() }
        }
    }

    It 'listens on the loopback address only' {
        $l = Start-LoopbackListener
        try {
            $l.LocalEndpoint.Address | Should -Be ([System.Net.IPAddress]::Loopback)
            $l.LocalEndpoint.Port | Should -BeGreaterThan 0
        } finally { $l.Stop() }
    }

    It 'ignores requests without state and returns the code from the matching answer' {
        $l = Start-LoopbackListener
        try {
            $c = Start-Client -Port $l.LocalEndpoint.Port -Paths @('/favicon.ico', '/?state=S-123&code=4%2F0Abc&scope=openid')
            $answer = Wait-AuthorizationCode -Listener $l -State 'S-123' -TimeoutSeconds 20
            $answer.Code | Should -BeExactly '4/0Abc'
            $statuses = $c.Shell.EndInvoke($c.Handle)
            $statuses[0] | Should -BeLike 'HTTP/1.1 404*'
            $statuses[1] | Should -BeLike 'HTTP/1.1 200*'
        } finally { $l.Stop() }
    }

    It 'stops on a wrong state' {
        $l = Start-LoopbackListener
        try {
            $c = Start-Client -Port $l.LocalEndpoint.Port -Paths @('/?state=WRONG&code=abc')
            { Wait-AuthorizationCode -Listener $l -State 'S-123' -TimeoutSeconds 20 } | Should -Throw '*state value did not match*'
            [void]$c.Shell.EndInvoke($c.Handle)
        } finally { $l.Stop() }
    }

    It 'stops on an error answer' {
        $l = Start-LoopbackListener
        try {
            $c = Start-Client -Port $l.LocalEndpoint.Port -Paths @('/?state=S-123&error=access_denied')
            { Wait-AuthorizationCode -Listener $l -State 'S-123' -TimeoutSeconds 20 } | Should -Throw '*access_denied*'
            [void]$c.Shell.EndInvoke($c.Handle)
        } finally { $l.Stop() }
    }

    It 'gives up after the timeout' {
        $l = Start-LoopbackListener
        try {
            { Wait-AuthorizationCode -Listener $l -State 'S-123' -TimeoutSeconds 1 } | Should -Throw '*No answer from Google*'
        } finally { $l.Stop() }
    }
}

Describe 'SSH arguments and host key' {
    It 'always uses a strict, non-interactive connection and ends with the operation' -TestCases @(@{ op = 'check' }, @{ op = 'update' }) {
        param($op)
        $a = Get-SshArguments -Target 'deploy@3.104.239.4' -KeyPath 'C:\keys\owner' -KnownHostsFile 'C:\Users\Asha Pal\.ssh\stocklist_known_hosts' -Operation $op
        $a | Should -Contain '-T'
        $a | Should -Contain 'BatchMode=yes'
        $a | Should -Contain 'IdentitiesOnly=yes'
        $a | Should -Contain 'StrictHostKeyChecking=yes'
        $a | Should -Contain 'UserKnownHostsFile="C:\Users\Asha Pal\.ssh\stocklist_known_hosts"'
        $a[-1] | Should -BeExactly $op
        $a[-2] | Should -BeExactly 'deploy@3.104.239.4'
        ($a -join ' ') | Should -Not -Match 'sudo|accept-new|StrictHostKeyChecking=no'
    }

    It 'allows only the check and update operations' {
        { Get-SshArguments -Target 'deploy@h' -KeyPath 'k' -KnownHostsFile 'f' -Operation 'bash' } | Should -Throw
    }

    It 'takes the host from the target' {
        Get-SshHost 'deploy@3-104-239-4.sslip.io' | Should -BeExactly '3-104-239-4.sslip.io'
        { Get-SshHost 'deploy@host;rm -rf /' } | Should -Throw
        { Get-SshHost 'host-only' } | Should -Throw
    }

    Context 'pinned host key' -Skip:(-not (Get-Command ssh-keygen -ErrorAction SilentlyContinue)) {
        BeforeAll {
            function script:New-HostKey([string]$Name) {
                $path = Join-Path $TestDrive $Name
                & ssh-keygen -q -t ed25519 -N '' -C '' -f $path | Out-Null
                $pubParts = (Get-Content "$path.pub" -Raw).Trim().Split(' ')
                $fp = ((& ssh-keygen -l -f "$path.pub") -split ' ')[1]
                return [pscustomobject]@{ Line = "$($pubParts[0]) $($pubParts[1])"; Fingerprint = $fp }
            }
            $script:Key1 = New-HostKey 'host1'
            $script:Key2 = New-HostKey 'host2'
        }

        It 'accepts the expected fingerprint' {
            $kh = Join-Path $TestDrive 'kh-good'
            Set-Content $kh "example.test $($script:Key1.Line)"
            { Assert-HostKeyPinned -HostName 'example.test' -KnownHostsFile $kh -Expected $script:Key1.Fingerprint } | Should -Not -Throw
        }

        It 'stops on a different fingerprint' {
            $kh = Join-Path $TestDrive 'kh-bad'
            Set-Content $kh "example.test $($script:Key2.Line)"
            { Assert-HostKeyPinned -HostName 'example.test' -KnownHostsFile $kh -Expected $script:Key1.Fingerprint } | Should -Throw '*does not match*'
        }

        It 'stops when any stored key for the host is not the expected one' {
            $kh = Join-Path $TestDrive 'kh-two'
            Set-Content $kh @("example.test $($script:Key1.Line)", "example.test $($script:Key2.Line)")
            { Assert-HostKeyPinned -HostName 'example.test' -KnownHostsFile $kh -Expected $script:Key1.Fingerprint } | Should -Throw '*does not match*'
        }

        It 'stops when the host is not in the file, or the file is missing' {
            $kh = Join-Path $TestDrive 'kh-other'
            Set-Content $kh "other.test $($script:Key1.Line)"
            { Assert-HostKeyPinned -HostName 'example.test' -KnownHostsFile $kh -Expected $script:Key1.Fingerprint } | Should -Throw '*not in*'
            { Assert-HostKeyPinned -HostName 'example.test' -KnownHostsFile (Join-Path $TestDrive 'missing') -Expected $script:Key1.Fingerprint } | Should -Throw '*not in*'
        }

        It 'refuses an expected value that is not a SHA256 fingerprint' {
            $kh = Join-Path $TestDrive 'kh-good2'
            Set-Content $kh "example.test $($script:Key1.Line)"
            { Assert-HostKeyPinned -HostName 'example.test' -KnownHostsFile $kh -Expected 'MD5:aa:bb' } | Should -Throw '*SHA256 fingerprint*'
        }
    }
}

Describe 'The whole flow' {
    BeforeAll {
        $script:Secret = 'GOCSPX-test-secret'
        $script:Refresh = '1//0gTestRefreshTokenValue-abcdefghijklmnopqrstuvwxyz'

        function script:New-TokenResponse {
            param($Claims = (Get-GoodClaims), [string]$Scope = "openid https://www.googleapis.com/auth/userinfo.email $script:GmailSend", [string]$Refresh = $script:Refresh)
            $r = [ordered]@{ access_token = 'ya29.test-access'; expires_in = 3599; scope = $Scope; token_type = 'Bearer'; id_token = (New-TestIdToken -Claims $Claims) }
            if ($Refresh) { $r.refresh_token = $Refresh }
            return [pscustomobject]$r
        }

        function script:Invoke-Flow {
            Invoke-GmailAuthorization -SshTarget 'deploy@3.104.239.4' -SshKey 'owner-key' -ExpectedHostKey 'SHA256:abc' `
                -Sender $script:Sender -KnownHostsFile 'kh'
        }
    }

    BeforeEach {
        # Every random value is the test nonce, so the ID tokens built here carry the nonce the flow expects.
        Mock -ModuleName GmailAuthorize New-RandomBase64Url { 'test-nonce-value' }
        Mock -ModuleName GmailAuthorize Assert-AgentHasKey { }
        Mock -ModuleName GmailAuthorize Assert-HostKeyPinned { }
        Mock -ModuleName GmailAuthorize Read-ClientCredentials { [pscustomobject]@{ ClientId = $script:ClientId; ClientSecret = $script:Secret } }
        Mock -ModuleName GmailAuthorize Open-Browser { }
        Mock -ModuleName GmailAuthorize Wait-AuthorizationCode { [pscustomobject]@{ Code = '4/0Abc'; Scope = '' } }
        Mock -ModuleName GmailAuthorize Get-GoogleJwks { $script:Jwks }
        Mock -ModuleName GmailAuthorize Invoke-TokenRevocation { }
        Mock -ModuleName GmailAuthorize Invoke-TokenExchange { New-TokenResponse }
        Mock -ModuleName GmailAuthorize Invoke-Ssh { [pscustomobject]@{ ExitCode = 0; StdOut = 'READY'; StdErr = '' } } -ParameterFilter { $Arguments[-1] -eq 'check' }
        Mock -ModuleName GmailAuthorize Invoke-Ssh { [pscustomobject]@{ ExitCode = 0; StdOut = 'UPDATED GMAIL_OAUTH_CLIENT_ID GMAIL_OAUTH_CLIENT_SECRET GMAIL_OAUTH_REFRESH_TOKEN'; StdErr = '' } } -ParameterFilter { $Arguments[-1] -eq 'update' }
    }

    It 'sends the three values only on SSH standard input after every check passed' {
        Mock -ModuleName GmailAuthorize Assert-IdToken { } # the real check uses the current time; covered above
        { Invoke-Flow } | Should -Not -Throw
        Should -Invoke -ModuleName GmailAuthorize Invoke-Ssh -Times 1 -Exactly -ParameterFilter { $Arguments[-1] -eq 'check' -and $InputLines.Count -eq 0 }
        Should -Invoke -ModuleName GmailAuthorize Invoke-Ssh -Times 1 -Exactly -ParameterFilter {
            $Arguments[-1] -eq 'update' -and $InputLines.Count -eq 3 -and $InputLines[0] -eq $script:ClientId -and
            $InputLines[1] -eq $script:Secret -and $InputLines[2] -eq $script:Refresh -and
            ($Arguments -join ' ') -notmatch [regex]::Escape($script:Refresh)
        }
        Should -Invoke -ModuleName GmailAuthorize Invoke-TokenRevocation -Times 0 -Exactly
    }

    It 'validates the real ID token in the flow' {
        Mock -ModuleName GmailAuthorize Invoke-TokenExchange {
            $c = Get-GoodClaims
            $c.iat = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() - 5
            $c.exp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + 3000
            New-TokenResponse -Claims $c
        }
        { Invoke-Flow } | Should -Not -Throw
        Should -Invoke -ModuleName GmailAuthorize Invoke-Ssh -Times 1 -Exactly -ParameterFilter { $Arguments[-1] -eq 'update' }
    }

    It 'never opens the browser when the server does not answer READY' -TestCases @(
        @{ exit = 255; out = '' }
        @{ exit = 2; out = '' }
        @{ exit = 0; out = 'NOT READY' }
    ) {
        param($exit, $out)
        Mock -ModuleName GmailAuthorize Invoke-Ssh { [pscustomobject]@{ ExitCode = $exit; StdOut = $out; StdErr = 'x' } } -ParameterFilter { $Arguments[-1] -eq 'check' }
        { Invoke-Flow } | Should -Throw '*not ready*'
        Should -Invoke -ModuleName GmailAuthorize Read-ClientCredentials -Times 0 -Exactly
        Should -Invoke -ModuleName GmailAuthorize Open-Browser -Times 0 -Exactly
        Should -Invoke -ModuleName GmailAuthorize Invoke-Ssh -Times 0 -Exactly -ParameterFilter { $Arguments[-1] -eq 'update' }
    }

    It 'stops before SSH when the key is not in the agent or the host key is not pinned' {
        Mock -ModuleName GmailAuthorize Assert-AgentHasKey { throw 'The SSH key is not loaded' }
        { Invoke-Flow } | Should -Throw '*not loaded*'
        Mock -ModuleName GmailAuthorize Assert-AgentHasKey { }
        Mock -ModuleName GmailAuthorize Assert-HostKeyPinned { throw 'does not match ExpectedHostKey' }
        { Invoke-Flow } | Should -Throw '*does not match*'
        Should -Invoke -ModuleName GmailAuthorize Invoke-Ssh -Times 0 -Exactly
        Should -Invoke -ModuleName GmailAuthorize Open-Browser -Times 0 -Exactly
    }

    It 'revokes the token and sends nothing when <name>' -TestCases @(
        @{ name = 'another Google account signed in'; field = 'email'; value = 'someone.else@gmail.com'; scope = $null }
        @{ name = 'the email is not verified'; field = 'email_verified'; value = $false; scope = $null }
        @{ name = 'an extra scope was granted'; field = $null; value = $null; scope = 'openid email https://www.googleapis.com/auth/gmail.send https://www.googleapis.com/auth/drive' }
        @{ name = 'gmail.send was not granted'; field = $null; value = $null; scope = 'openid email' }
    ) {
        param($name, $field, $value, $scope)
        Mock -ModuleName GmailAuthorize Invoke-TokenExchange {
            $c = Get-GoodClaims
            $c.iat = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() - 5
            $c.exp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + 3000
            if ($field) { $c[$field] = $value }
            if ($scope) { New-TokenResponse -Claims $c -Scope $scope } else { New-TokenResponse -Claims $c }
        }
        { Invoke-Flow } | Should -Throw
        Should -Invoke -ModuleName GmailAuthorize Invoke-TokenRevocation -Times 1 -Exactly -ParameterFilter { $Token -eq $script:Refresh }
        Should -Invoke -ModuleName GmailAuthorize Invoke-Ssh -Times 0 -Exactly -ParameterFilter { $Arguments[-1] -eq 'update' }
    }

    It 'revokes the access token when Google returned no refresh token' {
        Mock -ModuleName GmailAuthorize Invoke-TokenExchange { New-TokenResponse -Refresh '' }
        { Invoke-Flow } | Should -Throw '*no refresh token*'
        Should -Invoke -ModuleName GmailAuthorize Invoke-TokenRevocation -Times 1 -Exactly -ParameterFilter { $Token -eq 'ya29.test-access' }
    }

    It 'revokes the token when the server does not confirm the update' -TestCases @(
        @{ exit = 4; out = '' }
        @{ exit = 0; out = 'something else' }
    ) {
        param($exit, $out)
        Mock -ModuleName GmailAuthorize Assert-IdToken { }
        Mock -ModuleName GmailAuthorize Invoke-Ssh { [pscustomobject]@{ ExitCode = $exit; StdOut = $out; StdErr = '' } } -ParameterFilter { $Arguments[-1] -eq 'update' }
        { Invoke-Flow } | Should -Throw '*did not confirm*'
        Should -Invoke -ModuleName GmailAuthorize Invoke-TokenRevocation -Times 1 -Exactly -ParameterFilter { $Token -eq $script:Refresh }
    }

    It 'revokes the token when SSH breaks during the transfer' {
        Mock -ModuleName GmailAuthorize Assert-IdToken { }
        Mock -ModuleName GmailAuthorize Invoke-Ssh { throw 'Pipe is broken' } -ParameterFilter { $Arguments[-1] -eq 'update' }
        { Invoke-Flow } | Should -Throw '*Pipe is broken*'
        Should -Invoke -ModuleName GmailAuthorize Invoke-TokenRevocation -Times 1 -Exactly
    }
}

Describe 'No clipboard, no elevated commands' {
    It 'the helper never uses the clipboard or sudo' {
        foreach ($f in @('GmailAuthorize.psm1', 'gmail-authorize.ps1')) {
            $text = Get-Content (Join-Path $PSScriptRoot '..' $f) -Raw
            $text | Should -Not -Match '(?i)clipboard|clip\.exe|\bsudo\b'
        }
    }
}
