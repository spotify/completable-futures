#!groovy

node {
   stage('Checkout') {
      // Checkout code from repository
      checkout scm
   }
   env.JAVA_HOME="${tool 'JDK 9'}"
   // Get the maven tool.
   // ** NOTE: This 'M3' maven tool must be configured
   // **       in the global configuration.
   def mvnHome = tool 'M3'

   stage('Build') {
       // Run the maven build
       sh "${mvnHome}/bin/mvn clean deploy"
   }
}
