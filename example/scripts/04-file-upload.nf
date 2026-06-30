#!/usr/bin/env nextflow

/*
 * Example: File Upload from a Workflow
 *
 * Upload files to Slack at any point during execution.
 * Supports local files and remote URIs (s3://, az://, gs://).
 */

include { slackFileUpload } from 'plugin/nf-slack'

process GENERATE_REPORT {

    output:
        path 'qc_summary.csv'

    script:
    """
    cat <<-CSV > qc_summary.csv
    sample_id,reads,mapped_pct,mean_coverage,duplication_pct,status
    SAMPLE_001,48230156,97.3,32.1,12.4,PASS
    SAMPLE_002,52104833,98.1,35.7,11.2,PASS
    SAMPLE_003,31056742,62.4,8.3,45.6,FAIL
    SAMPLE_004,44879210,96.8,29.4,13.1,PASS
    SAMPLE_005,50321477,97.9,33.8,10.7,PASS
    CSV
    """
}

workflow {
    GENERATE_REPORT()

    // Simple upload
    GENERATE_REPORT.out.map { myFile ->
        slackFileUpload(file: myFile)
    }

    // Upload with metadata
    GENERATE_REPORT.out.map { myFile ->
        slackFileUpload(
            file: myFile,
            title: 'QC Summary',
            comment: 'Sample quality control metrics from pipeline run'
        )
    }
}
