# Work Plan: Complete File Upload Support for nf-slack

## Reference

- GitHub Issue: #30 - "feature: Upload file as part of message"
- Current State: Implementation exists at HEAD (commit aee50eb)
- Status: Core feature implemented, needs verification and documentation

## Analysis Summary

The file upload feature has been implemented with:

- V2 Slack API upload flow (3-step: get URL → upload content → complete)
- Extension functions: `slackFileUpload(String)` and `slackFileUpload(Map)`
- Threading support when `useThreads` is enabled
- Graceful error handling for all failure paths
- 13 new test cases

**Gaps Identified:**

1. No HTTP-mocked tests for the 3-step flow (only error paths tested)
2. Missing documentation (README, examples)
3. Missing required Slack scopes documentation
4. No example configs showing usage
5. Potential Content-Length header issue on file upload

## Tasks

### Task 1: Verify Implementation Completeness

**Status:** [x]
**Description:** Run full test suite, verify build passes, check for any obvious bugs
**Expected Outcome:**

- All tests pass
- Build succeeds
- No compilation errors
- Manual code review of BotSlackSender.uploadFile() flow

### Task 2: Add HTTP-Mocked Integration Tests

**Status:** [x]
**Description:** Create tests that verify the 3-step upload flow with mocked HTTP responses
**Expected Outcome:**

- Test verifies files.getUploadURLExternal called with correct params
- Test verifies upload to pre-signed URL with correct content
- Test verifies files.completeUploadExternal called with file_id from step 1
- Test verifies thread_ts passed when threading enabled

### Task 3: Update Documentation

**Status:** [x]
**Description:** Add file upload documentation to README and examples
**Expected Outcome:**

- README updated with slackFileUpload() usage examples
- Required Slack scopes documented (files:write)
- Configuration example showing both simple and advanced usage
- Threading behavior explained

### Task 4: Create Example Configuration

**Status:** [x]
**Description:** Add example nextflow.config showing file upload patterns
**Expected Outcome:**

- Example 10: Basic file upload
- Example 11: File upload with metadata (title, comment)
- Example 12: File upload in threaded conversation

### Task 5: Final Verification

**Status:** [x]
**Description:** End-to-end verification of the complete feature
**Expected Outcome:**

- All tests pass including new ones
- Documentation reviewed for accuracy
- Examples validated
- Ready to close issue #30

## Task Dependencies

```
Task 1 ─┬─> Task 2 ─┐
        └─> Task 3 ─┼─> Task 5
        └─> Task 4 ─┘
```

## Success Criteria

- [x] All 5 tasks completed
- [x] Tests pass: `./gradlew test`
- [x] Build succeeds: `./gradlew assemble`
- [x] Documentation complete and accurate
- [x] Examples demonstrate real-world usage patterns
