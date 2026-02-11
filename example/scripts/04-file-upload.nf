#!/usr/bin/env nextflow

/*
 * Example: File Upload from a Workflow
 *
 * Upload files to Slack at any point during execution.
 * Supports local files and remote URIs (s3://, az://, gs://).
 */

include { slackFileUpload } from 'plugin/nf-slack'

process GENERATE_REPORT {
    publishDir 'results', mode: 'copy'

    output:
        path 'report.html'

    script:
    """
    echo '<html><body><h1>Analysis Report</h1></body></html>' > report.html
    """
}

workflow {
    GENERATE_REPORT()

    // Simple upload
    slackFileUpload("${workflow.launchDir}/results/report.html")

    // Upload with metadata
    slackFileUpload(
        file: "${workflow.launchDir}/results/report.html",
        title: 'Analysis Report',
        comment: 'Pipeline results attached'
    )
}
