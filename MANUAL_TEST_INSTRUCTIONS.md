# Manual Test Instructions for ORF Proxy Feature

## Test Case 1: Verify Test Proxy Preference is Visible
1. Install the debug APK on an Android device/emulator
2. Open Zapp app
3. Navigate to Settings
4. Look for "Network" section
5. **Expected**: You should see a "Test proxy connection" / "Proxy-Verbindung testen" preference

## Test Case 2: Test Proxy Functionality
1. In Settings > Network, tap on "Test proxy connection"
2. **Expected**: A message "Testing proxy connection..." should appear
3. **Expected**: After a few seconds, either "Proxy connection successful" or "Proxy connection failed" should appear
4. Note: The actual result depends on network environment and proxy availability

## Test Case 3: Verify ORF Proxy Usage Logging  
1. Try to access an ORF channel (if available in the channel list)
2. **Expected**: In debug logs (timber), you should see messages about proxy usage when accessing ORF content
3. Note: This requires actual ORF content to be accessed

## Test Case 4: Verify No Regression in Settings
1. Navigate through all other settings options
2. **Expected**: All existing settings should work as before
3. Test changing UI mode, language, stream quality settings
4. **Expected**: No crashes or issues

## Code Validation
- The new feature is properly integrated with existing settings system
- Uses established patterns (PreferenceFragmentHelper, Snackbar notifications)
- Minimal changes focused on the specific request
- Proper internationalization (German and English)
- Network tests are environment-independent and won't fail in CI