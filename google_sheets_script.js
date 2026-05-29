// Google Apps Script for PulseFit Registration & Profile Sync
// Instructions:
// 1. Open your Google Sheet: https://docs.google.com/spreadsheets/d/1DQbBvvaS9kh3_iV_bqT3Y482s2XoQPQP8c_LyWA2y1Q/edit
// 2. Go to Extensions > Apps Script.
// 3. Delete any code in the editor, and paste the code below.
// 4. Click the Save icon (floppy disk).
// 5. Click "Deploy" > "New deployment".
// 6. Select type: "Web app".
// 7. Change "Who has access" to "Anyone" (crucial so the Android app can send logs without custom Google account sign-in).
// 8. Click "Deploy" and authorize the permissions using your Google account.
// 9. Copy the generated Web App URL and paste it under the Webhook panel in your PulsFit Profile Settings screen.

function doPost(e) {
  try {
    var jsonString = e.postData.contents;
    var data = JSON.parse(jsonString);
    
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    
    // Check if headers exist, if not create them automatically
    if (sheet.getLastRow() === 0) {
      sheet.appendRow([
        "Name", 
        "Email", 
        "Phone", 
        "Gender", 
        "Date of Birth", 
        "Height (cm)", 
        "Weight (kg)", 
        "Workout Days/Week", 
        "Daily Workout Status", 
        "Avatar Type", 
        "Last Sync Time"
      ]);
      // Apply clean Material styling to column headers
      sheet.getRange(1, 1, 1, 11).setFontWeight("bold").setBackground("#B3E5FC").setHorizontalAlignment("center");
    }
    
    var emailIdx = 1; // Column B (which is index 1 in 0-based index)
    var emailToFind = data.email || "";
    var foundRow = -1;
    
    if (emailToFind) {
      var lastRow = sheet.getLastRow();
      if (lastRow > 1) {
        var values = sheet.getRange(2, 2, lastRow - 1, 1).getValues(); // Retrieve all email values (Column B)
        for (var i = 0; i < values.length; i++) {
          if (values[i][0] === emailToFind) {
            foundRow = i + 2; // Row numbers are 1-based, starting below headers
            break;
          }
        }
      }
    }
    
    var formattedDate = new Date().toLocaleString();
    var rowData = [
      data.name || "",
      data.email || "",
      data.phone || "",
      data.gender || "",
      data.dob || "",
      data.height || 0,
      data.weight || 0,
      data.workoutDaysPerWeek || 0,
      data.isWorkoutDaily ? "Yes" : "No",
      data.avatarResName || "",
      formattedDate
    ];
    
    if (foundRow > -1) {
      // Update existing record to prevent duplicates
      sheet.getRange(foundRow, 1, 1, rowData.length).setValues([rowData]);
    } else {
      // Append a brand new registration row
      sheet.appendRow(rowData);
    }
    
    return ContentService.createTextOutput(JSON.stringify({
      "status": "success",
      "message": "User details successfully synchronized to Google Sheet",
      "row": foundRow > -1 ? foundRow : sheet.getLastRow()
    })).setMimeType(ContentService.MimeType.JSON);
    
  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({
      "status": "error",
      "message": error.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

function doGet(e) {
  return ContentService.createTextOutput("PulseFit Sync Webhook is Active! Send HTTP POST requests to register health metrics.")
    .setMimeType(ContentService.MimeType.TEXT);
}
