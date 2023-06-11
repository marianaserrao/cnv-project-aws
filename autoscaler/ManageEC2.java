package autoscaler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.util.HashMap;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;

public class ManageEC2 {
    private static String AWS_REGION = "us-east-1";
    private static String AMI_ID = "ami-0189da5c270e2f478";
    private static String KEY_NAME = "cnv-aws";
    private static String SEC_GROUP_ID = "sg-0c5a7c7da31c8f941";

    private static long OBS_TIME = 1000 * 60 * 20;
    private static long ITERATION_TIME = 1000 * 60 * 1;
    private static double MAX_CPU = 10;
    private static double MIN_CPU = 5;

    private static AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard()
                    .withRegion(AWS_REGION)
                    .withCredentials(new EnvironmentVariableCredentialsProvider())
                    .build();
    private static AmazonCloudWatch cloudWatch = AmazonCloudWatchClientBuilder.standard()
                .withRegion(AWS_REGION)
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .build();

    private static Map<String, Object[]> activeInstances = new HashMap<>();
    private static List<String> instancesToTerminate = new ArrayList<>();

    public static Instance launchInstance() throws Exception{
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
    
    public static void terminateInstance(String instanceID) throws Exception{
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
    
    private static Set<Instance> getInstances() throws Exception {
        Set<Instance> instances = new HashSet<Instance>();
        for (Reservation reservation : ec2.describeInstances().getReservations()) {
            instances.addAll(reservation.getInstances());
        }
        return instances;
    }

    private static double getInstanceMeanCPUUtilization(String instanceId, Dimension instanceDimension){
        
        instanceDimension.setValue(instanceId);
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest().withStartTime(new Date(new Date().getTime() - OBS_TIME))
                                                                            .withNamespace("AWS/EC2")
                                                                            .withPeriod(60)
                                                                            .withMetricName("CPUUtilization")
                                                                            .withStatistics("Average")
                                                                            .withDimensions(instanceDimension)
                                                                            .withEndTime(new Date());
        List<Datapoint> datapoints = cloudWatch.getMetricStatistics(request).getDatapoints();
        System.out.println(instanceId + " : "+ datapoints);
        double dpSum = 0.0;
        int dpSize = datapoints.size();
        double iCPUMeanUtilization = 0.0;
        if(dpSize>0){
            for (Datapoint dp : datapoints) {
                dpSum += dp.getAverage();
            }
            iCPUMeanUtilization = dpSum / dpSize;
        }
        return iCPUMeanUtilization;
    }

    private static void scaleInstances() throws Exception{
        try {
            terminateUnusedInstances();
            boolean pendingInstances = false;

            Set<Instance> instances = getInstances();
            Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            
            double CPUUtilizationSum = 0.0;
            int instanceCount = 0;
            
            for (Instance instance : instances) {
                String iid = instance.getInstanceId();
                String state = instance.getState().getName();
                if (state.equals("running")) { 
                    instanceCount+=1;
                    double iCPUMeanUtilization = getInstanceMeanCPUUtilization(iid, instanceDimension);                
                    CPUUtilizationSum+=iCPUMeanUtilization;
                    System.out.println(iid+ " CPU Utilization: " + iCPUMeanUtilization);
                    if (iCPUMeanUtilization<=MIN_CPU){
                        System.out.println("Low CPU Utilization");
                        setInstanceTermination(instance.getInstanceId());
                    }
                }else if(state.equals("pending")){
                    pendingInstances=true;
                }
            }
            double CPUUtilizationMean = CPUUtilizationSum/instanceCount;
            if(CPUUtilizationMean>=MAX_CPU && !pendingInstances){
                System.out.println("High CPU Utiization");
                handleInstanceLaunch();
            }
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    private static boolean instanceIsRunning(Instance instance){
        String state = instance.getState().getName();
        return state.equals("running");
    }

    private static void handleInstanceLaunch() throws Exception{
        Instance newInstance = launchInstance();
        activeInstances.put(newInstance.getInstanceId(), new Object[]{newInstance, null});
    }

    private static void setInstanceTermination(String instanceId) {
        if(activeInstances.size()>1){
            instancesToTerminate.add(instanceId);
            activeInstances.remove(instanceId);
        }
    }

    private static void terminateUnusedInstances() throws Exception{
        Iterator<String> iterator = instancesToTerminate.iterator();
        while (iterator.hasNext()) {
            String instanceId = iterator.next();
            terminateInstance(instanceId);
            iterator.remove();
        }
    }

    public static Map<String, Object[]> activeInstancesGetter(){
        return activeInstances;
    }

    public static void main(String[] args) throws Exception{
        // handles preexisting instances
        Set<Instance> instances = getInstances();
        for (Instance instance: instances){
            if(instanceIsRunning(instance)){
                activeInstances.put(instance.getInstanceId(), new Object[]{instance, null});
            }
        }
        if(activeInstances.size()<2){
            handleInstanceLaunch();
            handleInstanceLaunch();
        }
        while(instances.size()>0){
            System.out.println("instances to terminate: "+instancesToTerminate);
            System.out.println("intances running: "+activeInstances);
            scaleInstances();
            Thread.sleep(ITERATION_TIME);
        }
    }
}

