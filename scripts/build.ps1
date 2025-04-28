param(
    [Parameter(Mandatory=$true)] [pscredential]$Credential,
    [Parameter(Mandatory=$true)] [string]$Product
)

Write-Host "Executing build tasks for product: $Product"
Write-Host "Credential User: $($Credential.UserName)"

# Simulate execution
Write-Host "Performing validation and build tasks..."
Start-Sleep -Seconds 5

Write-Host "Tasks completed successfully."
