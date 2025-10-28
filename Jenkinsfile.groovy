def UNITY_V = "6000.0.53f1"
def UNITY_INSTALLATION = "/opt/unity/editors/6000.0.53f1/Editor"

  
pipeline {
    agent any
    environment {
        PROJECT_PATH = "${env.WORKSPACE}"
        NEXTCLOUD_URL = "http://127.0.0.1/remote.php/dav/files/admin"
        NEXTCLOUD_USERNAME = "admin"
        NEXTCLOUD_PASSWORD = "admin"
        BUILD_FOLDER = "Builds"
        UNITY_HEAP_SIZE = "2048m"
        GLOBAL_MEMORY_LIMIT = "2048"
        JAVA_OPTS = "-Xmx2g -Xms512m"
    }


    stages {        
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
  

        stage('Check Commit Message') {
            steps {
                script {
                    def commitMessage = sh(script: 'git log -1 --pretty=%B', returnStdout: true).trim()
                    print(commitMessage)

                    if (!commitMessage.endsWith('-b')) {
                        error("Build skipped: Commit message does not end with '-b'")
                    }
                }
            }
        }


        stage('Create Swap') {
            steps {
                sh '''
                sudo swapoff -a 2>/dev/null || true
                sudo dd if=/dev/zero of=/swapfile bs=1M count=4096 status=progress
                sudo chmod 600 /swapfile
                sudo mkswap /swapfile
                sudo swapon /swapfile
                '''
            }


        }
        stage('Get Project Name') {
            steps {
                script {
                    def tmpFile = "/tmp/unity_project_name.txt"

                    sh """
                    Xvfb :99 -screen 0 640x480x8 &
                    XVFB_PID=\$!
                    export DISPLAY=:99

                    "${UNITY_INSTALLATION}/Unity" -quit -batchmode -nographics -projectPath "${env.WORKSPACE}" -executeMethod Autobuild.GetProjectName -logFile -
                    pkill -f "${UNITY_INSTALLATION}/Unity" 2>/dev/null || true
                    kill \$XVFB_PID 2>/dev/null || true
                    """

                    sleep time: 2, unit: 'SECONDS'


                    def lines = readFile(file: tmpFile).trim().split('\n')
                    env.PROJECT_NAME = lines[0].trim()
                    env.BUILD_TARGET = lines.size() > 1 ? lines[1].trim() : 'null-groovy'
                }
            }
        }


        stage('Build Artefact') {
            steps {
                script {
                    sh """
                    mkdir -p Builds
                    Xvfb :99 -screen 0 640x480x8 &
                    XVFB_PID=\$!
                    export DISPLAY=:99
                    export UNITY_HEAP_SIZE="${UNITY_HEAP_SIZE}"
                    export GLOBAL_MEMORY_LIMIT="2048"


                    timeout 1800 "${UNITY_INSTALLATION}/Unity" \\
                    -quit \\
                    -batchmode \\
                    -nographics \\
                    -projectPath "${env.WORKSPACE}" \\
                    -executeMethod Autobuild.MakeBuild \\
                    -logfile -


                    pkill -f "${UNITY_INSTALLATION}/Unity" 2>/dev/null || true
                    kill \$XVFB_PID 2>/dev/null || true
                    """
  

                    def date = new Date()
                    def timestamp = date.format('yyyyMMdd_HHmmss', TimeZone.getTimeZone('Europe/Moscow'))


                    sh """
                    cd Builds
                    LATEST_ZIP=\$(ls -t *.zip 2>/dev/null | head -n 1)
                    if [ -n "\$LATEST_ZIP" ]; then
                        NEW_FILENAME="${env.PROJECT_NAME}-${env.BUILD_TARGET}-build-${env.BUILD_NUMBER}-${timestamp}.zip"
                        mv "\$LATEST_ZIP" "\$NEW_FILENAME"
                    else
                        exit 1
                    fi
                    """
                }
            }
        }

  

        stage('Deploy Artefact') {
            steps {
                script {
                    sh '''
                    LATEST_ZIP=$(ls -t Builds/*.zip 2>/dev/null | head -n 1)
                    FILE_NAME=$(basename "$LATEST_ZIP")
                    curl -u "${NEXTCLOUD_USERNAME}:${NEXTCLOUD_PASSWORD}" \\
                         -X MKCOL "${NEXTCLOUD_URL}/builds/${PROJECT_NAME}" || true
                    curl -u "${NEXTCLOUD_USERNAME}:${NEXTCLOUD_PASSWORD}" \\
                         -T "$LATEST_ZIP" "${NEXTCLOUD_URL}/builds/${PROJECT_NAME}/$FILE_NAME"
                    '''
                }
            }
        }
    }

  

    post {
        always {
            script {
                archiveArtifacts artifacts: "Builds/*.zip", allowEmptyArchive: true
                sh "rm -f /tmp/unity_project_name.txt || true"
            }

  
            sh '''
            pkill -f Xvfb 2>/dev/null || true
            pkill -f Unity 2>/dev/null || true
            sudo swapoff /swapfile 2>/dev/null || true
            sudo rm -f /swapfile 2>/dev/null || true
            '''
        }

        failure {
            echo 'Build failed!'
        }
    }

    options {
        skipDefaultCheckout(true)
        timeout(time: 120, unit: 'MINUTES')
    }
}