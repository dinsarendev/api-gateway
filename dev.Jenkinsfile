pipeline {
    agent any

    environment {
        PROJECT_SERVICE  = "API GATEWAY SERVICE 🗃️"
        GIT_BRANCH = "develop"
        GIT_REPO_URL = "https://github.com/Cambofreelance-Software-Development/api-gateway.git"
        GIT_CREDENTIALS_ID = "github_credentials"

        // manifest
        GIT_REPO_MANIFEST_URL = "https://github.com/Cambofreelance-Software-Development/micro-manifest.git"
        GIT_REPO_MANIFEST_UPDATE_URL = "github.com/Cambofreelance-Software-Development/micro-manifest.git"
        GIT_MANIFEST_BRANCH = "dev"
        MANIFEST_FOLDER = "dev/overlays/gateway/patches"
        SERVICE_PATCH = "api-gateway-service-patch.yaml"

        IMAGE_REGISTRY = "nexus.cambofreelance.com/docker-hosted"
        FOLDER_REGISTRY = "ms/dev"
        IMAGE_NAME = "api-gateway-service"
        DOCKER_REPO_PATH = "${IMAGE_REGISTRY}/${FOLDER_REGISTRY}/${IMAGE_NAME}"

        TELEGRAM_CHAT_ID  = '-1003570206702'
        TELEGRAM_TOPIC_ID = '2'

        // SonarQube
        SONARQUBE_PROJECT_APP = "API GATEWAY"
        SONARQUBE_PROJECT_KEY = "API-GATEWAY"
        SONARQUBE_PROJECT_URL = "https://sonar-qube.cambofreelance.com"
    }
    stages {
        stage('Checkout Code') {
            steps {
                echo "🔀 Checking out application branch ${env.GIT_BRANCH}"
                git branch:        env.GIT_BRANCH,
                    url:           env.GIT_REPO_URL,
                    credentialsId: env.GIT_CREDENTIALS_ID

                script {
                    // ✅ Call function after checkout
                    //def sha = getGitCommitSHA()
                    def sha = env.GIT_COMMIT
                    env.GIT_COMMIT_MESSAGE = sh(script: 'git log -1 --pretty=%B', returnStdout: true).trim()
                    env.GIT_COMMIT_SHA    = sha
                    env.GIT_COMMIT_SHORT  = sha.take(7)
                    env.DOCKER_FULL_IMAGE = "${env.DOCKER_REPO_PATH}:${sha}"


                    echo "📋 Branch     : ${env.GIT_BRANCH}"
                    echo "📋 Full SHA   : ${env.GIT_COMMIT_SHA}"
                    echo "📋 Short SHA  : ${env.GIT_COMMIT_SHORT}"
                    echo "📋 Docker Tag : ${env.DOCKER_FULL_IMAGE}"

                }
            }
        }

        stage('SonarQube Analysis') {
            withSonarQubeEnv() {
                sh "./gradlew sonar"
            }
        }

        stage('Docker Build Image') {
            steps {
                script {
                    echo "Build Docker Image"

                    sh '''
                        echo "Current directory:"
                        pwd
                        echo ""

                        echo "Checking Dockerfile..."
                        if [ -f Dockerfile ]; then
                            echo "✅ Dockerfile found"
                            cat Dockerfile
                        else
                            echo "❌ Dockerfile not found!"
                            exit 1
                        fi

                        echo ""
                        echo "Building Docker image: ${DOCKER_FULL_IMAGE}"
                        docker build -t ${DOCKER_FULL_IMAGE} .

                        echo ""
                        echo "✅ Docker images created:"
                        docker images | grep ${DOCKER_FULL_IMAGE}
                    '''
                }
            }
        }
        stage('Push Docker Image') {
            steps {
                script {
                    sh '''
                        echo "✅ Push Docker Image"
                        docker push ${DOCKER_FULL_IMAGE}
                    '''
                }
            }
        }
        stage('Update Manifest') {
            steps {
                script {
                    // Clone repository
                    git branch: "${GIT_MANIFEST_BRANCH}",
                        credentialsId: "${GIT_CREDENTIALS_ID}",
                        url: "${GIT_REPO_MANIFEST_URL}"

                    // Update image tag and push changes
                    withCredentials([usernamePassword(
                        credentialsId: "${GIT_CREDENTIALS_ID}",
                        usernameVariable: 'GIT_USERNAME',
                        passwordVariable: 'GIT_PASSWORD'
                    )]) {
                        sh """
                            cd ${MANIFEST_FOLDER}

                            sed -E -i 's|^([[:space:]]*image:[[:space:]]*).*\$|\\1${DOCKER_FULL_IMAGE}|' "${SERVICE_PATCH}"

                            echo "Updated manifest:"
                            cat "${SERVICE_PATCH}"

                            git config user.email "jenkins@ci.local"
                            git config user.name "Jenkins"
                            git add "${SERVICE_PATCH}"
                            git commit -m "Update ${SERVICE_PATCH} to ${DOCKER_FULL_IMAGE}"
                            git push https://${GIT_USERNAME}:${GIT_PASSWORD}@${GIT_REPO_MANIFEST_UPDATE_URL} HEAD:${GIT_MANIFEST_BRANCH}
                        """
                    }
                }
            }
        }
        stage('Clean Docker Image') {
            steps {
                script {
                    sh '''
                        echo "🧹 Cleaning Docker Image"
                        docker rmi ${DOCKER_FULL_IMAGE}
                    '''
                }
            }
        }
    }
    post {
        success {
            script {
                sendTelegramNotification('SUCCESS')
            }
        }
        failure {
            script {
                sendTelegramNotification('FAILED')
            }
        }
        always {
            script {
                echo "🧹 Cleaning workspace..."
                deleteDir()
            }
        }
    }
}
// HELPER FUNCTION
def sendTelegramNotification(String status) {
    def emoji    = status == 'SUCCESS' ? '✅' : '❌'
    def buildUrl = env.BUILD_URL
    def buildNo  = env.BUILD_NUMBER
    def sonarLink = "https://sonar-qube.cambofreelance.com/dashboard?id=${SONARQUBE_PROJECT_KEY}"

    def message = """
${emoji} <b>Building: ${env.PROJECT_SERVICE}</b>

🌿 <b>Branch:</b>  <code>${env.GIT_BRANCH}</code>
🔖 <b>Commit:</b>  <code>${env.GIT_COMMIT_MESSAGE}</code>
🐳 <b>Image:</b>   <code>${env.DOCKER_FULL_IMAGE}</code>
🚓 <b>SonarQube:</b>   <code>${sonarLink}</code>
""".trim()

    withCredentials([
        string(credentialsId: 'telegram_bot_token', variable: 'BOT_TOKEN')
    ]) {
        sh """
            curl -s -X POST https://api.telegram.org/bot\${BOT_TOKEN}/sendMessage \\
            -d chat_id=${env.TELEGRAM_CHAT_ID} \\
            -d message_thread_id=${env.TELEGRAM_TOPIC_ID} \\
            -d parse_mode=HTML \\
            --data-urlencode text='${message}'
        """
    }
}