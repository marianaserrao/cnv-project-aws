# Cloud Computing and Virtualization Project - Auto Scaler and Load Balancer (AWS Java SDK)

## How to Run this Module

## Only AutoScaler
javac -cp <path-to-aws-sdk>/lib/aws-java-sdk-1.12.486.jar:<path-to-aws-sdk>/third-party/lib/*:. autoscaler/*.java 

java -cp <path-to-aws-sdk>/lib/aws-java-sdk-1.12.486.jar:<path-to-aws-sdk>/third-party/lib/*:. autoscaler.AutoScaler

## LoadBalancer

javac -cp <path-to-org.json>/json-20230227.jar:<path-to-aws-sdk>/lib/aws-java-sdk-1.12.486.jar:<path-to-aws-sdk>/third-party/lib/*:. loadbalancer/*.java 

java -cp <path-to-org.json>/json-20230227.jar:<path-to-aws-sdk>/lib/aws-java-sdk-1.12.486.jar:<path-to-aws-sdk>/third-party/lib/*:. loadbalancer.LoadBalancer