package ch.softozor.pipeline

def prepareBackendConfiguration(e2eJps, backendJpsUrl) {
  sh "curl -o $backendJps $backendJpsUrl/manifest.jps"
  sh "curl -o $e2eJps $backendJpsUrl/e2e/reset_database.jps"
}

def prepareFrontendConfiguration(frontendName, frontendJps, e2eJps) {
  sh "sed -i \"s/FRONTEND_NAME/$frontendName/g\" $frontendJps"
  sh "sed -i \"s/FRONTEND_NAME/$frontendName/g\" $e2eJps"
}

def publishBackendDockerImage(backendName, branch, enableDevTools, imageType) {
  DOCKER_REPO = "softozor/$backendName:$imageType-$branch"
  sh "docker login -u $DOCKER_CREDENTIALS_USR -p $DOCKER_CREDENTIALS_PSW"
  sh "docker build --build-arg ENABLE_DEV_TOOLS=$enableDevTools --network=host -t $DOCKER_REPO ."
  sh "docker push $DOCKER_REPO"
}

def publishFrontendDockerImage(frontendName, branch, graphqlApi, enableDevTools, imageType) {
  DOCKER_REPO = "softozor/$frontendName:$imageType-$branch"
  sh "cp ./common/$imageType/Dockerfile ."
  sh "cp ./common/$imageType/.dockerignore ."
  sh "docker login -u $DOCKER_CREDENTIALS_USR -p $DOCKER_CREDENTIALS_PSW"
  sh "docker build --build-arg GRAPHQL_API=$graphqlApi --build-arg ENABLE_DEV_TOOLS=$enableDevTools --network=host -t $DOCKER_REPO ."
  sh "docker push $DOCKER_REPO"
}

def deploy(backendJps, backendEnvName, branch, imageType) {
  sh "dos2unix ./common/e2e/helpers.sh"
  sh "sed -i \"s/APP_NAME/$backendEnvName/g\" $backendJps"
  sh "sed -i \"s/IMAGE_TYPE/$imageType/g\" $backendJps"
  sh "sed -i \"s/BRANCH/$branch/g\" $backendJps"
  SCRIPT_TO_RUN = './common/e2e/deploy-to-jelastic.sh'
  sh "chmod u+x $SCRIPT_TO_RUN"
  sh "dos2unix $SCRIPT_TO_RUN"
  sh "$SCRIPT_TO_RUN $JELASTIC_APP_CREDENTIALS_USR $JELASTIC_APP_CREDENTIALS_PSW $JELASTIC_CREDENTIALS_USR $JELASTIC_CREDENTIALS_PSW $backendEnvName cp $backendJps $tag"
}

def tagAndPush(tag, description) {
  originUrl = "https://$GITHUB_CREDENTIALS_USR:$GITHUB_CREDENTIALS_PSW@" + GIT_URL.drop(8)
  sh "git remote rm origin"
  sh "git remote add origin $originUrl"
  sh "git tag $tag -m \"$description\""
  sh "git push origin $tag"
  sh "git checkout $tag"
}

def runE2eTests(e2eJps, envName) {
  sh "dos2unix ./common/e2e/helpers.sh"
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
  sh "dos2unix ./common/e2e/helpers.sh"
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
  sh "dos2unix ./common/e2e/helpers.sh"
  SCRIPT_TO_RUN = './common/e2e/stop-jelastic-env.sh'
  sh "dos2unix $SCRIPT_TO_RUN"
  sh "chmod u+x $SCRIPT_TO_RUN"
  sh "$SCRIPT_TO_RUN $JELASTIC_APP_CREDENTIALS_USR $JELASTIC_APP_CREDENTIALS_PSW $JELASTIC_CREDENTIALS_USR $JELASTIC_CREDENTIALS_PSW $envName"
}

return this