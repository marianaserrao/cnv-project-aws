package autoscaler;

import java.util.HashSet;
import java.util.Set;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;

public class EC2 {
  private static String AWS_REGION = System.getenv("AWS_DEFAULT_REGION");
  private static String AMI_ID = System.getenv("DEFAULT_AMI_ID");
                                  	
  private static String KEY_NAME = System.getenv("AWS_KEYPAIR_NAME");
  private static String SEC_GROUP_ID = System.getenv("SEC_GROUP_ID");

  private static AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard()
              .withRegion(AWS_REGION)
              .withCredentials(new EnvironmentVariableCredentialsProvider())
              .build();

  static Instance launchInstance() throws Exception{
    try {             
      System.out.println("Starting a new instance.");
      RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
      runInstancesRequest.withImageId(AMI_ID)
                      .withInstanceType("t2.micro")
                      .withMinCount(1)
                      .withMaxCount(1)
                      .withKeyName(KEY_NAME)
                      .withSecurityGroupIds(SEC_GROUP_ID);
      RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
      Instance newInstance = runInstancesResult.getReservation().getInstances().get(0);
      return newInstance;
    }catch (AmazonServiceException ase) {
      System.out.println("Caught Exception: " + ase.getMessage());
      System.out.println("Reponse Status Code: " + ase.getStatusCode());
      System.out.println("Error Code: " + ase.getErrorCode());
      System.out.println("Request ID: " + ase.getRequestId());
      return null;
    }
  }
  
  static void terminateInstance(String instanceID) throws Exception{
    try{
        System.out.println("Terminating instance "+instanceID);
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceID);
        ec2.terminateInstances(termInstanceReq);  
    } catch (AmazonServiceException ase) {
        System.out.println("Caught Exception: " + ase.getMessage());
        System.out.println("Reponse Status Code: " + ase.getStatusCode());
        System.out.println("Error Code: " + ase.getErrorCode());
        System.out.println("Request ID: " + ase.getRequestId());
    }    
  }  
  
  static Set<Instance> getAllInstances() throws Exception {
    Set<Instance> instances = new HashSet<Instance>();
    for (Reservation reservation : ec2.describeInstances().getReservations()) {
        instances.addAll(reservation.getInstances());
    }
    return instances;
  }

  static Instance getInstance(String instanceId) {
        DescribeInstancesRequest request = new DescribeInstancesRequest()
                .withInstanceIds(instanceId);
        DescribeInstancesResult response = ec2.describeInstances(request);
        Reservation reservation = response.getReservations().get(0);
        Instance instance = reservation.getInstances().get(0);
    return instance;
  }

  static String getAWSRegion(){
    return AWS_REGION;
  }
}
