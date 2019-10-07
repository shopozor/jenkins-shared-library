package ch.softozor.pipeline

def getBackendE2eManifests(installJps, e2eJps, jpsBaseUrl) {
  sh "curl -o $installJps $jpsBaseUrl/e2e/install.jps"
  sh "curl -o $e2eJps $jpsBaseUrl/e2e/e2e.jps"
}

def getFrontendE2eManifests(installJps, e2eJps, jpsBaseUrl) {
  sh "curl -o $installJps $jpsBaseUrl/manifest.jps"
  sh "curl -o $e2eJps $jpsBaseUrl/e2e/e2e.jps"
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

def getJelasticScript(scriptFile) {
  sh "curl -o $scriptFile https://raw.githubusercontent.com/shopozor/jelastic-shared-library/master/$scriptFile"
  sh "dos2unix $scriptFile"
  sh "chmod u+x $scriptFile"
}

def deploy(installJps, appName, envName, branch, imageType) {
  tag = "$imageType-$branch"
  sh "sed -i \"s/APP_NAME/$appName/g\" $installJps"
  sh "sed -i \"s/IMAGE_TYPE/$imageType/g\" $installJps"
  sh "sed -i \"s/BRANCH/$branch/g\" $installJps"
  getJelasticScript('helpers.sh')
  SCRIPT_TO_RUN = 'deploy-to-jelastic.sh'
  getJelasticScript(SCRIPT_TO_RUN)
  sh "./$SCRIPT_TO_RUN $JELASTIC_APP_CREDENTIALS_USR $JELASTIC_APP_CREDENTIALS_PSW $JELASTIC_CREDENTIALS_USR $JELASTIC_CREDENTIALS_PSW $envName cp $installJps $tag"
}

def tagAndPush(tag, description) {
  originUrl = "https://$GITHUB_CREDENTIALS_USR:$GITHUB_CREDENTIALS_PSW@" + GIT_URL.drop(8)
  sh "git remote rm origin"
  sh "git remote add origin $originUrl"
  sh "git tag $tag -m \"$description\""
  sh "git push origin $tag"
  sh "git checkout $tag"
}

def runE2eTests(e2eJps, envName, appName, imageType) {
  sh "sed -i \"s/APP_NAME/$appName/g\" $e2eJps"
  sh "sed -i \"s/IMAGE_TYPE/$imageType/g\" $e2eJps"
  getJelasticScript('helpers.sh')
  SCRIPT_TO_RUN = 'run-e2e.sh'
  getJelasticScript(SCRIPT_TO_RUN)
  sh "./$SCRIPT_TO_RUN $JELASTIC_APP_CREDENTIALS_USR $JELASTIC_APP_CREDENTIALS_PSW $JELASTIC_CREDENTIALS_USR $JELASTIC_CREDENTIALS_PSW $envName cp $e2eJps"
}

def resetDatabase(e2eJps, envName, appName, imageType) {
  runE2eTests(e2eJps, envName, appName, imageType)
}

def deleteFolder(folderName) {
  dir(folderName) {
    deleteDir()
  }
}

def mountRemoteFolder(targetEnvName, targetNodeGroup, targetPath, sourceEnvName, sourceNodeGroup, sourcePath) {
  getJelasticScript('helpers.sh')
  SCRIPT_TO_RUN = 'mount-remote-folder.sh'
  getJelasticScript(SCRIPT_TO_RUN)
  sh "./$SCRIPT_TO_RUN $JELASTIC_APP_CREDENTIALS_USR $JELASTIC_APP_CREDENTIALS_PSW $JELASTIC_CREDENTIALS_USR $JELASTIC_CREDENTIALS_PSW $targetEnvName $targetNodeGroup $targetPath $sourceEnvName $sourceNodeGroup $sourcePath" 
}

def retrieveTestResults(jenkinsEnvName, targetNodeGroup, targetPath, frontendName, sourceNodeGroup) {
  sh "rm -Rf ${frontendName}"
  mountRemoteFolder($jenkinsEnvName, $targetNodeGroup, $targetPath, $frontendName, $sourceNodeGroup, $PATH_TO_TEST_RESULTS)
  sh "cp -R ${targetPath}/cypress/${SCREENSHOTS_FOLDER} ./${frontendName}/${SCREENSHOTS_FOLDER}"
  sh "cp -R ${targetPath}/cypress/${VIDEOS_FOLDER} ./${frontendName}/${VIDEOS_FOLDER}"
  sh "cp -R ${targetPath}/${TEST_REPORTS_FOLDER} ./${frontendName}/${TEST_REPORTS_FOLDER}"
}

def buildArtifacts() {
  archiveArtifacts artifacts: "**/${VIDEOS_FOLDER}/**/*.mp4, **/${SCREENSHOTS_FOLDER}/**/*.png"
  junit "**/${TEST_REPORTS_FOLDER}/*.xml"
}

def stopEnvironment(envName) {
  getJelasticScript('helpers.sh')
  SCRIPT_TO_RUN = 'stop-jelastic-env.sh'
  getJelasticScript(SCRIPT_TO_RUN)
  sh "./$SCRIPT_TO_RUN $JELASTIC_APP_CREDENTIALS_USR $JELASTIC_APP_CREDENTIALS_PSW $JELASTIC_CREDENTIALS_USR $JELASTIC_CREDENTIALS_PSW $envName"
}

def generateSpecification(featureDir, repo, commitNb) {
  sh "mono /opt/pickles/Pickles.exe --feature-directory=$featureDir --output-directory=specification --system-under-test-name=$repo --system-under-test-version=$commitNb --language=fr --documentation-format=dhtml --exp --et 'in-preparation'"
  sh "sshpass -p $SOFTOZOR_CREDENTIALS_PSW ssh -o StrictHostKeyChecking=no $SOFTOZOR_CREDENTIALS_USR@softozor.ch 'rm -Rf ~/www/www.softozor.ch/shopozor/$repo/*'"
  sh "sshpass -p $SOFTOZOR_CREDENTIALS_PSW scp -o StrictHostKeyChecking=no -r specification/* $SOFTOZOR_CREDENTIALS_USR@softozor.ch:~/www/www.softozor.ch/shopozor/$repo/"
}

return this