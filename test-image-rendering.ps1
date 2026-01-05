# PowerShell script to test PDF generation with base64 images
# This will call your API endpoint and save the PDF for inspection

$payloadPath = "src\main\resources\jsonPayloads\payload3.json"
$outputPath = "test-output.pdf"
$endpoint = "http://localhost:8080/api/content/submit"

Write-Host "Testing PDF Generation with Base64 Images..." -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
Write-Host ""

# Check if payload exists
if (-Not (Test-Path $payloadPath)) {
    Write-Host "ERROR: Payload file not found at $payloadPath" -ForegroundColor Red
    exit 1
}

# Read the JSON payload
$payload = Get-Content $payloadPath -Raw

Write-Host "1. Sending POST request to $endpoint" -ForegroundColor Cyan

try {
    # Make the API call
    $response = Invoke-WebRequest -Uri $endpoint `
        -Method POST `
        -ContentType "application/json" `
        -Body $payload `
        -OutFile $outputPath `
        -PassThru

    if ($response.StatusCode -eq 200) {
        Write-Host "2. Success! PDF generated and saved to: $outputPath" -ForegroundColor Green
        Write-Host ""
        Write-Host "File size: $((Get-Item $outputPath).Length) bytes" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Opening PDF in default viewer..." -ForegroundColor Cyan
        Start-Process $outputPath
        Write-Host ""
        Write-Host "INSTRUCTIONS:" -ForegroundColor Yellow
        Write-Host "1. Check the footer on each page - you should see a small logo image on the left" -ForegroundColor White
        Write-Host "2. If the image is missing, check the console logs for DEBUG messages" -ForegroundColor White
        Write-Host "3. Look for 'Footer contains data URI: true/false' in the logs" -ForegroundColor White
    } else {
        Write-Host "ERROR: Unexpected status code: $($response.StatusCode)" -ForegroundColor Red
    }
} catch {
    Write-Host "ERROR: Failed to generate PDF" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    Write-Host "Make sure your Spring Boot application is running on port 8080" -ForegroundColor Yellow
    Write-Host "Run: mvn spring-boot:run" -ForegroundColor Cyan
}
