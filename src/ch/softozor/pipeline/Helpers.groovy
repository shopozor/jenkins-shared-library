package ch.softozor.pipeline

def prepareBackendConfiguration(gitUser, gitPwd, gitBranch, backendJps, e2eJps, backendJpsUrl) {
  sh "curl -o $backendJps $backendJpsUrl/manifest.jps"
  sh "curl -o $e2eJps $backendJpsUrl/e2e.jps"
  sh "sed -i \"s/GIT_USER/$gitUser/g\" $backendJps"
  sh "sed -i \"s/GIT_PASSWORD/$gitPwd/g\" $backendJps"
  sh "sed -i \"s/GIT_BRANCH/$gitBranch/g\" $backendJps"
}

def prepareFrontendConfiguration(frontendName, frontendJps, e2eJps) {
  sh "sed -i \"s/FRONTEND_NAME/$frontendName/g\" $frontendJps"
  sh "sed -i \"s/FRONTEND_NAME/$frontendName/g\" $e2eJps"
}

def buildDockerImage() {
  sh "docker login -u $DOCKER_CREDENTIALS_USR -p $DOCKER_CREDENTIALS_PSW"
  sh "cp e2e/Dockerfile ."
  sh "docker build -t $DOCKER_REPO ."
  sh "docker push $DOCKER_REPO"
}

def deploy(backendJps, backendEnvName) {
  SCRIPT_TO_RUN = './common/e2e/deploy-to-jelastic.sh'
  sh "dos2unix $SCRIPT_TO_RUN"
  sh "chmod u+x $SCRIPT_TO_RUN"
  sh "$SCRIPT_TO_RUN $JELASTIC_APP_CREDENTIALS_USR $JELASTIC_APP_CREDENTIALS_PSW $JELASTIC_CREDENTIALS_USR $JELASTIC_CREDENTIALS_PSW $backendEnvName cp $backendJps"
}

def runE2eTests(e2eJps, envName) {
  SCRIPT_TO_RUN = './common/e2e/run-e2e.sh'
  sh "dos2unix $SCRIPT_TO_RUN"
  sh "chmod u+x $SCRIPT_TO_RUN"
  sh "$SCRIPT_TO_RUN $JELASTIC_APP_CREDENTIALS_USR $JELASTIC_APP_CREDENTIALS_PSW $JELASTIC_CREDENTIALS_USR $JELASTIC_CREDENTIALS_PSW $envName cp $e2eJps"
}

def resetDatabase(e2eJps, envName) {
  runE2eTests(e2eJps, envName)
}

def deleteFolder(folderName) {
  dir(folderName) {
    deleteDir()
  }
}

def retrieveTestResults(jenkinsEnvName, targetNodeGroup, targetPath, frontendName, sourceNodeGroup) {
  deleteFolder(TEST_REPORTS_FOLDER)
  deleteFolder(VIDEOS_FOLDER)
  deleteFolder(SCREENSHOTS_FOLDER)
  SCRIPT_TO_RUN = './common/e2e/mount-test-results.sh'
  sh "dos2unix $SCRIPT_TO_RUN"
  sh "chmod u+x $SCRIPT_TO_RUN"
  sh "$SCRIPT_TO_RUN $JELASTIC_APP_CREDENTIALS_USR $JELASTIC_APP_CREDENTIALS_PSW $JELASTIC_CREDENTIALS_USR $JELASTIC_CREDENTIALS_PSW $jenkinsEnvName $targetNodeGroup $targetPath $frontendName $sourceNodeGroup $PATH_TO_TEST_RESULTS"
  sh "cp -R ${targetPath}/cypress/${SCREENSHOTS_FOLDER} ."
  sh "cp -R ${targetPath}/cypress/${VIDEOS_FOLDER} ."
  sh "cp -R ${targetPath}/${TEST_REPORTS_FOLDER} ."
}

def buildArtifacts() {
  archiveArtifacts artifacts: "${VIDEOS_FOLDER}/**/*.mp4, ${SCREENSHOTS_FOLDER}/**/*.png"
  junit "${TEST_REPORTS_FOLDER}/*.xml"
}

def stopEnvironment(envName) {
  SCRIPT_TO_RUN = './common/e2e/stop-jelastic-env.sh'
  sh "dos2unix $SCRIPT_TO_RUN"
  sh "chmod u+x $SCRIPT_TO_RUN"
  sh "$SCRIPT_TO_RUN $JELASTIC_APP_CREDENTIALS_USR $JELASTIC_APP_CREDENTIALS_PSW $JELASTIC_CREDENTIALS_USR $JELASTIC_CREDENTIALS_PSW $envName"
}

return this