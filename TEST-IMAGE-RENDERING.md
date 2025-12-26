# Testing Base64 Image Rendering in PDF

## What We Changed

We implemented **Option 1**: Extract images from data URIs, save them to temp files, and rewrite references to use file paths.

### Why This Approach?

Option 2 (Jsoup parsing) preserved the data URIs correctly, but iText7 was unable to render them in the PDF. The logs showed:
- ✅ Data URI was intact after decoding
- ✅ Jsoup preserved the img tags
- ❌ But images didn't appear in the PDF

This confirmed that iText7 has issues with data URIs in certain contexts (headers/footers), so we switched to file-based images.

### Changes Made:

1. **MarkdownService.java**
   - Added `extractAndSaveImages()` method to extract data URI images and save them as files
   - Added `parseDataUri()` to decode base64 data and determine file extensions
   - Added `ImageData` helper class to hold extracted image data
   - Modified `configureFinalDom()` to process headers/footers through image extraction
   - Updated `createConverterProperties()` to use current working directory as base URI
   - Renamed `SecureDataUriResourceRetriever` to `SecureFileResourceRetriever` (simplified)
   - Added `cleanupTempFiles()` method with shutdown hook for automatic cleanup

2. **application.properties**
   - Enabled DEBUG logging for MarkdownService

3. **.gitignore**
   - Added `temp/` and `test-output.pdf` to ignore list

## How It Works

### Image Extraction Process

1. **Detection**: Scans HTML for `<img>` tags with `src` starting with `data:`
2. **Parsing**: Extracts media type and base64 data from data URI
3. **File Creation**:
   - Creates `temp/images/` directory if it doesn't exist
   - Generates unique filename using UUID + appropriate extension (jpg, png, gif, etc.)
   - Writes decoded binary data to file
4. **Rewriting**: Updates `img src` to reference the file path (e.g., `temp/images/abc123.jpg`)
5. **Cleanup**: Shutdown hook automatically deletes temp files when application stops

### Base URI Configuration

- Base URI is set to current working directory
- iText7 resolves relative paths like `temp/images/xyz.jpg` from this base URI
- Maintains SSRF protection by blocking HTTP/HTTPS requests

## How to Test

### Step 1: Build and Run the Application

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

Wait for the application to start (look for "Started FlexMarkProjectApplication" in the console).

### Step 2: Run the Test Script

In a new terminal/PowerShell window:

```powershell
.\test-image-rendering.ps1
```

This script will:
1. Send a POST request with payload3.json (which contains base64 image in footer)
2. Save the generated PDF as `test-output.pdf`
3. Open the PDF in your default viewer

### Step 3: Check the Results

#### In the PDF:
- Look at the **footer** on each page
- You should see a small logo image on the **left side** of the footer
- The **right side** should show "Page X"

#### In the Console Logs:
Look for DEBUG messages from MarkdownService:

```
Processing footer - HTML length: XXXX
Footer contains data URI: true
Found 1 data URI image(s) to extract
Created temp directory: C:\projects\flexMarkProject\temp\images
Saved image to: C:\projects\flexMarkProject\temp\images\abc-123.jpg
Rewrote img src to: temp/images/abc-123.jpg
Footer processed successfully
Using base URI: file:///C:/projects/flexMarkProject/
PDF generated successfully, size: XXXXX bytes
```

#### In the File System:
- Check the `temp/images/` directory - you should see the extracted image file(s)
- The file will be automatically deleted when the application shuts down

## What to Look For

### ✅ SUCCESS Indicators:
- `Found 1 data URI image(s) to extract`
- `Saved image to: ...temp/images/xyz.jpg`
- `Rewrote img src to: temp/images/xyz.jpg`
- Image appears in the PDF footer

### ❌ FAILURE Indicators:
- `Failed to extract image from data URI` → Check the error message
- `No data URI images found` → Data URI was lost during decoding
- Image does NOT appear in PDF → Check base URI configuration

## Troubleshooting

### Image Doesn't Appear

1. **Check if image was extracted:**
   ```powershell
   ls temp\images\
   ```
   You should see `.jpg` files with UUID names

2. **Check the logs for base URI:**
   ```
   Using base URI: file:///C:/projects/flexMarkProject/
   ```

3. **Manually verify the image file:**
   - Open the `.jpg` file in the temp folder
   - Make sure it's a valid image

### Permission Errors

If you see errors creating the temp directory:
```bash
mkdir temp\images
```

### Images Not Cleaned Up

The cleanup happens on graceful shutdown. If you forcefully kill the app (Ctrl+C), run:
```powershell
rm -r temp
```

## Manual Testing

You can also test manually using curl or Postman:

```bash
curl -X POST http://localhost:8080/api/content/submit \
  -H "Content-Type: application/json" \
  -d @src/main/resources/jsonPayloads/payload3.json \
  --output test-output.pdf
```

Then open `test-output.pdf` to check if the image appears in the footer.

## Next Steps

If this works (which it should!), you can:

1. **Add more images** to your templates - they'll all be extracted automatically
2. **Optimize cleanup** - Currently cleans up on shutdown, could also clean up after each PDF generation
3. **Cache images** - Could reuse extracted images if the same data URI appears multiple times
4. **Add image validation** - Verify image data is valid before saving
