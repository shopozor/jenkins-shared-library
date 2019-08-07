def call() {
  def helpers = new ch.softozor.pipeline.Helpers()
  pipeline {
    agent any
    environment {
      REPORTS_FOLDER = 'junit-reports'    
    }
    stages {
      stage('Node Modules Installation') {
        steps {
          sh "CYPRESS_CACHE_FOLDER=$WORKSPACE/.cache yarn"
        }
      }
      stage('Building application') {
        environment {
          GRAPHQL_API = 'http://localhost:8000/graphql/'
        }
        steps {
          sh "yarn build"
        }
      }
      stage('Performing unit tests') {
        steps {
          script {
            helpers.deleteFolder(REPORTS_FOLDER)
            sh "yarn test:unit:ci"
          }
        }
      }
    }
    post {
      always {
        junit "**/$REPORTS_FOLDER/*.xml"
      }
    }
  }
}