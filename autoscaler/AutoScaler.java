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

public class AutoScaler {
    private static long OBS_TIME = 1000 * 60 * 20;
    private static long ITERATION_TIME = 1000 * 60 * 20;
    private static double MAX_CPU = 70;
    private static double MIN_CPU = 5;

    private static AmazonCloudWatch cloudWatch = AmazonCloudWatchClientBuilder.standard()
                .withRegion(EC2.getAWSRegion())
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .build();

    private static Map<String, Object[]> runningInstances = new HashMap<>();
    private static List<String> pendingInstances = new ArrayList<>();
    private static List<String> instancesToTerminate = new ArrayList<>();

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
        double dpSum = 0.0;
        int dpSize = datapoints.size();
        double iCPUMeanUtilization = 0.0;
        if(dpSize>0){
            for (Datapoint dp : datapoints) {
                System.out.println(instanceId + " CPU datapoint: "+ dp);
                dpSum += dp.getAverage();
            }
            iCPUMeanUtilization = dpSum / dpSize;
        }
        return iCPUMeanUtilization;
    }

    private static void scaleInstances() throws Exception{
        try {
            terminateUnusedInstances();
            pendingInstances.clear();
            runningInstances.clear();

            Set<Instance> instances = EC2.getAllInstances();
            Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            
            double CPUUtilizationSum = 0.0;
            int instanceCount = 0;

            List<String> unusedInstances = new ArrayList<>();
            
            for (Instance instance : instances) {
                String iid = instance.getInstanceId();
                String state = instance.getState().getName();
                if (state.equals("running")) { 
                    System.out.println();
                    double iCPUMeanUtilization = getInstanceMeanCPUUtilization(iid, instanceDimension);                
                    System.out.println(iid+ " CPU Utilization: " + iCPUMeanUtilization);
                    double requestsCost = 0;
                    runningInstances.put(iid, new Object[]{instance, iCPUMeanUtilization, requestsCost});
                    CPUUtilizationSum+=iCPUMeanUtilization;
                    instanceCount+=1;
                    if (iCPUMeanUtilization<=MIN_CPU){
                        System.out.println("Low CPU Utilization");
                        unusedInstances.add(instance.getInstanceId());
                    }
                }else if(state.equals("pending")){
                    pendingInstances.add(iid);
                }
            }
            double CPUUtilizationMean = CPUUtilizationSum/instanceCount;
            if((MAX_CPU-CPUUtilizationMean)/10 > pendingInstances.size() && unusedInstances.size()==0){
                System.out.println("High CPU Utiization");
                int launches = (int) Math.ceil((MAX_CPU-CPUUtilizationMean)/10)-pendingInstances.size();
                for (int i = 0; i < launches; i++) {
                    handleInstanceLaunch();
                }
            }else if(CPUUtilizationMean<MAX_CPU){
                for(String instanceId: unusedInstances){
                    setInstanceTermination(instanceId);
                }
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
        Instance newInstance = EC2.launchInstance();
        pendingInstances.add(newInstance.getInstanceId());
    }

    private static void setInstanceTermination(String instanceId) {
        if(runningInstances.size()>1){
            instancesToTerminate.add(instanceId);
            runningInstances.remove(instanceId);
        }
    }

    private static void terminateUnusedInstances() throws Exception{
        Iterator<String> iterator = instancesToTerminate.iterator();
        while (iterator.hasNext()) {
            String instanceId = iterator.next();
            EC2.terminateInstance(instanceId);
            iterator.remove();
        }
    }

    public static void checkPendingInstances() {

        Iterator<String> iterator = pendingInstances.iterator();
        while (iterator.hasNext()) {
            String instanceID = iterator.next();
            Instance instance = EC2.getInstance(instanceID);
            if(instanceIsRunning(instance)){
                double requestsCost = 0;
                runningInstances.put(instance.getInstanceId(), new Object[]{instance, null, requestsCost});
                iterator.remove();
            }
        }        
    }

    public static Map<String, Object[]> getRunningInstances(){
        checkPendingInstances();
        return runningInstances;
    }

    public static void main(String[] args) throws Exception{
        // create first intance
        handleInstanceLaunch();
        // autoscales
        while(runningInstances.size()>0 || pendingInstances.size()>0){      
            scaleInstances();
            
            System.out.println("\ninstances to terminate: "+instancesToTerminate);
            System.out.println("instances pending: "+pendingInstances.size());
            System.out.println("intances running: "+runningInstances);

            Thread.sleep(ITERATION_TIME);
        }
    }
}

