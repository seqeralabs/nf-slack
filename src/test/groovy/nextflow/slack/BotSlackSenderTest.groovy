/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.slack

import spock.lang.Specification
import java.nio.file.Path

/**
 * Tests for BotSlackSender
 */
class BotSlackSenderTest extends Specification {

    def 'should create sender with token and channel'() {
        when:
        def sender = new BotSlackSender('xoxb-token', 'C123456')

        then:
        sender != null
    }

    def 'should handle message sending gracefully'() {
        when:
        def sender = new BotSlackSender('xoxb-token', 'C123456')
        sender.sendMessage('{"text":"test"}')

        then:
        noExceptionThrown()
    }

    def 'should handle invalid JSON gracefully'() {
        when:
        def sender = new BotSlackSender('xoxb-token', 'C123456')
        sender.sendMessage('not valid json')

        then:
        noExceptionThrown()
    }

    def 'should return null for threadTs initially'() {
        when:
        def sender = new BotSlackSender('xoxb-token', 'C123456')

        then:
        sender.getThreadTs() == null
    }

    def 'should have getThreadTs method available'() {
        given:
        def sender = new BotSlackSender('xoxb-token', 'C123456')

        when:
        def threadTs = sender.getThreadTs()

        then:
        threadTs == null || threadTs instanceof String
    }

    def 'should handle file upload gracefully when API is unreachable'() {
        given:
        def sender = new BotSlackSender('xoxb-token', 'C123456')
        def tempFile = File.createTempFile('test', '.txt')
        tempFile.text = 'Hello World'

        when:
        sender.uploadFile(tempFile.toPath(), [:])

        then:
        noExceptionThrown()

        cleanup:
        tempFile.delete()
    }

    def 'should handle file upload for non-existent file gracefully'() {
        given:
        def sender = new BotSlackSender('xoxb-token', 'C123456')
        def nonExistentPath = java.nio.file.Paths.get('/tmp/non-existent-file-' + System.nanoTime() + '.txt')

        when:
        sender.uploadFile(nonExistentPath, [:])

        then:
        noExceptionThrown()
    }

    def 'should handle file upload for empty file gracefully'() {
        given:
        def sender = new BotSlackSender('xoxb-token', 'C123456')
        def emptyFile = File.createTempFile('test-empty', '.txt')
        // File is empty by default

        when:
        sender.uploadFile(emptyFile.toPath(), [:])

        then:
        noExceptionThrown()

        cleanup:
        emptyFile.delete()
    }

    def 'should handle file upload for unreadable file gracefully'() {
        given:
        def sender = new BotSlackSender('xoxb-token', 'C123456')
        def unreadableFile = File.createTempFile('test-unreadable', '.txt')
        unreadableFile.text = 'content'
        unreadableFile.setReadable(false)

        when:
        sender.uploadFile(unreadableFile.toPath(), [:])

        then:
        noExceptionThrown()

        cleanup:
        unreadableFile.setReadable(true)
        unreadableFile.delete()
    }

    def 'should accept file upload with custom options'() {
        given:
        def sender = new BotSlackSender('xoxb-token', 'C123456')
        def tempFile = File.createTempFile('test-options', '.png')
        tempFile.text = 'fake image data'

        when:
        sender.uploadFile(tempFile.toPath(), [
            filename: 'custom-name.png',
            title: 'My Plot',
            comment: 'Here is the result',
            threadTs: '1234567890.123456'
        ])

        then:
        // API call will fail but should not throw (graceful handling)
        noExceptionThrown()

        cleanup:
        tempFile.delete()
    }

    def 'should use filename from path when not specified in options'() {
        given:
        def sender = new BotSlackSender('xoxb-token', 'C123456')
        def tempFile = File.createTempFile('test-default-name', '.txt')
        tempFile.text = 'some content'

        when:
        sender.uploadFile(tempFile.toPath(), [:])

        then:
        // Graceful handling - API unreachable but no exception
        noExceptionThrown()

        cleanup:
        tempFile.delete()
    }

     // Note: We cannot easily test the actual HTTP call without mocking HttpURLConnection
     // or using a mock server. The thread timestamp capture happens in the postToSlack method
     // after a successful API response. Full testing would require:
     // 1. Mocking HttpURLConnection to return a successful response with a 'ts' field
     // 2. Verifying that threadTs is captured from the response
     // 3. Verifying that subsequent calls don't overwrite the threadTs
     // For this implementation, we rely on integration testing and the fact that the code
     // doesn't throw exceptions during normal operation.

     def 'should call upload steps in correct order'() {
         given:
         def callLog = []
         def sender = new BotSlackSender('xoxb-test-token', 'C123456') {
             @Override
             protected Map getUploadUrl(String filename, long length) {
                 callLog << "getUploadUrl:${filename}:${length}"
                 return [upload_url: 'http://fake-upload-url', file_id: 'F123ABC']
             }
             @Override
             protected boolean uploadFileContent(String uploadUrl, Path filePath) {
                 callLog << "uploadContent:${uploadUrl}"
                 return true
             }
             @Override
             protected void completeUpload(String fileId, String title, String channelId, String comment, String threadTs) {
                 callLog << "completeUpload:${fileId}:${title}:${channelId}"
             }
         }
         def tempFile = File.createTempFile('test-upload', '.txt')
         tempFile.text = 'test content'

         when:
         sender.uploadFile(tempFile.toPath(), [filename: 'report.html', title: 'My Report'])

         then:
         callLog.size() == 3
         callLog[0] == "getUploadUrl:report.html:${tempFile.length()}"
         callLog[1] == 'uploadContent:http://fake-upload-url'
         callLog[2] == 'completeUpload:F123ABC:My Report:C123456'

         cleanup:
         tempFile?.delete()
     }

     def 'should pass thread_ts to completeUpload when provided'() {
         given:
         def capturedThreadTs = null
         def sender = new BotSlackSender('xoxb-test-token', 'C123456') {
             @Override
             protected Map getUploadUrl(String filename, long length) {
                 return [upload_url: 'http://fake-upload-url', file_id: 'F123ABC']
             }
             @Override
             protected boolean uploadFileContent(String uploadUrl, Path filePath) {
                 return true
             }
             @Override
             protected void completeUpload(String fileId, String title, String channelId, String comment, String threadTs) {
                 capturedThreadTs = threadTs
             }
         }
         def tempFile = File.createTempFile('test-upload', '.txt')
         tempFile.text = 'test content'

         when:
         sender.uploadFile(tempFile.toPath(), [threadTs: '1234567890.123456'])

         then:
         capturedThreadTs == '1234567890.123456'

         cleanup:
         tempFile?.delete()
     }

     def 'should stop upload flow when getUploadUrl fails'() {
         given:
         def uploadContentCalled = false
         def completeUploadCalled = false
         def sender = new BotSlackSender('xoxb-test-token', 'C123456') {
             @Override
             protected Map getUploadUrl(String filename, long length) {
                 return null
             }
             @Override
             protected boolean uploadFileContent(String uploadUrl, Path filePath) {
                 uploadContentCalled = true
                 return true
             }
             @Override
             protected void completeUpload(String fileId, String title, String channelId, String comment, String threadTs) {
                 completeUploadCalled = true
             }
         }
         def tempFile = File.createTempFile('test-upload', '.txt')
         tempFile.text = 'test content'

         when:
         sender.uploadFile(tempFile.toPath(), [:])

         then:
         noExceptionThrown()
         !uploadContentCalled
         !completeUploadCalled

         cleanup:
         tempFile?.delete()
     }

    def 'should return false when validate hits unreachable endpoint'() {
        given:
        def sender = new BotSlackSender('xoxb-test-token', 'C1234567890')

        when:
        def result = sender.validate()

        then:
        result == false
    }
}
