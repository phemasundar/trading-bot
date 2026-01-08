pipeline {
    agent any

    tools {
        maven 'Maven'
        jdk 'JDK21'
    }

    environment {
        // Inject credentials from Jenkins Credentials Store
        REFRESH_TOKEN = credentials('trading-bot-refresh-token')
        APP_KEY = credentials('trading-bot-app-key')
        PP_SECRET = credentials('trading-bot-pp-secret')
        FINNHUB_API_KEY = credentials('trading-bot-finnhub-api-key')
        FMP_API_KEY = credentials('trading-bot-fmp-api-key')
        TELEGRAM_BOT_TOKEN = credentials('trading-bot-telegram-token')
        TELEGRAM_CHAT_ID = credentials('trading-bot-telegram-chat-id')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh 'mvn clean test'
            }
        }
    }

    post {
//         always {
//             // Archive test results
//             junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
//         }
        success {
            echo 'Build and tests completed successfully!'
        }
        failure {
            echo 'Build or tests failed.'
        }
    }
}
