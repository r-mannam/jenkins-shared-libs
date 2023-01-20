#!/usr/bin/env groovy

def call(Map config) {

pipeline {
    agent {
        label "${config.agent}"
    }
    stages {    
        stage('Functional Tests') {
            steps {
                //withVault([configuration: config.vault, vaultSecrets: config.secrets]) {
                    dir('pipeline/cucumber-framework') {
                        sh "mvn --batch-mode ${config.mvnArgs ?: ''} test"

                        cucumber failedFeaturesNumber: -1, failedScenariosNumber: -1, failedStepsNumber: -1, fileIncludePattern: 'target/cucumber.json', pendingStepsNumber: -1, skippedStepsNumber: -1, sortingMethod: 'ALPHABETICAL', undefinedStepsNumber: -1
                        archiveArtifacts artifacts: 'target/testAccessibility*.txt', allowEmptyArchive: true, fingerprint: false
                    }
                //}
            }
        }
       
        stage('Load Tests') {
            steps {
                dir('pipeline/jmeter') {
                    sh "jmeter -j jmeter.save.saveservice.output_format=xml -n -t Raven_IAC.jmx -l jenkins.io.report.jtl"
                    perfReport 'jenkins.io.report.jtl'
                }
            }
        }

        /*stage('Security Scan') {
            steps {
                script {
                    sh """/bin/rm -rf /tmp/speed-hermes.session*
                    java -jar /opt/zap/zap-2.10.0.jar -quickurl ${env.APP_UI_BASE_URL} -newsession "/tmp/speed-hermes.session" -cmd
                    report_name=\"Vulnerability Report - speed-hermes\"
                    prepared_by=\"SPEED\"
                    prepared_for=\"USCIS\"
                    scan_date=\$(date)
                    report_date=\$(date)
                    scan_version=\"N/A\"
                    report_version=\"N/A\"
                    report_description=\"Speed Hermes vulnerability report.\"
                    file_name=\"/tmp/speed-hermes.session\"
                    java -jar /opt/zap/zap-2.10.0.jar -export_report /tmp/speed-hermes.xhtml -source_info \"\$report_name;\$prepared_by;\$prepared_for;\$scan_date;\$report_date;\$scan_version;\$report_version;\$report_description\" -alert_severity \"t;t;f;t\" -alert_details \"t;t;t;t;t;t;f;f;f;f\" -session \"\$file_name\" -cmd
                    """
                    publishHTML (target: [
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: false,
                        reportDir: '/tmp',
                        reportFiles: 'speed-hermes.xhtml',
                        reportName: "OWASP ZAP Report"
                    ])
                }
            }
        }*/        
        
    }      
}

}
