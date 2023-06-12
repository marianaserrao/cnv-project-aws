# Cloud Computing and Virtualization Project - Auto Scaler and Load Balancer (AWS Java SDK)

source aws/config.sh
javac -cp aws/aws-sdk/lib/aws-java-sdk-1.12.486.jar:aws/aws-sdk/third-party/lib/*:. autoscaler/ManageEC2.java 
java -cp aws/aws-sdk/lib/aws-java-sdk-1.12.486.jar:aws/aws-sdk/third-party/lib/*:. autoscaler.ManageEC2

## Dependencies and Module installation

1. Environment variables and private keys for AWS authentification are needed to run this project
2. The project contains specifications that are only suited for the proposed case
3. The AWS SDK must be installed
4. The AWS CLI must be in PATH

## How to Run 
javac -cp <path-to-aws-sdk>/lib/aws-java-sdk-1.12.486.jar:<path-to-aws-sdk>/third-party/lib/*:. autoscaler/*.java 

java -cp <path-to-aws-sdk>/lib/aws-java-sdk-1.12.486.jar:<path-to-aws-sdk>/third-party/lib/*:. autoscaler.AutoScaler