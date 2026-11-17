#Requires -Version 5.1
<#
.SYNOPSIS
  Authorizes the Stock List Manager server to send email through the Gmail API, and sends the token to the server
  over SSH. Run on the owner's Windows computer. Full procedure: docs/EMAIL_SETUP.md.

.DESCRIPTION
  1. Checks SSH first (key loaded in the OpenSSH agent, pinned server host key, server answers READY).
  2. Opens Google sign-in in the browser (loopback 127.0.0.1, PKCE, state, nonce).
  3. Validates what Google returns (scopes, refresh token, ID token signature and claims, exact sender account).
  4. Sends client id, client secret and refresh token to the server on SSH standard input only.
  Nothing is written to disk on this computer. If anything fails after Google issued the token, the token is revoked.
  Secrets stay in this PowerShell process's memory until it ends (.NET strings cannot be wiped): close the window
  when it is done.

  -CheckOnly does step 1 only: it tests the restricted SSH key against the server (for example after a deployment)
  without contacting Google or sending anything.

.EXAMPLE
  .\gmail-authorize.ps1 -SshTarget deploy@3.104.239.4 -SshKey $HOME\.ssh\stocklist_owner -ExpectedHostKey SHA256:AbCd...

.EXAMPLE
  .\gmail-authorize.ps1 -SshTarget deploy@3.104.239.4 -SshKey $HOME\.ssh\stocklist_owner -ExpectedHostKey SHA256:AbCd... -CheckOnly
#>
param(
    [Parameter(Mandatory)][string]$SshTarget,
    [Parameter(Mandatory)][string]$SshKey,
    [Parameter(Mandatory)][string]$ExpectedHostKey,
    [string]$Sender = 'suppliermanagement.mail@gmail.com',
    [string]$KnownHostsFile = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.ssh\stocklist_known_hosts'),
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'GmailAuthorize.psm1') -Force
try {
    Invoke-GmailAuthorization -SshTarget $SshTarget -SshKey $SshKey -ExpectedHostKey $ExpectedHostKey `
        -Sender $Sender -KnownHostsFile $KnownHostsFile -CheckOnly:$CheckOnly
} catch {
    Write-Host ''
    Write-Host "STOPPED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
