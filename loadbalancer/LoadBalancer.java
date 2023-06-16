package loadbalancer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.awt.image.BufferedImage;
import java.util.stream.Collectors;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.net.URL;
import java.lang.Math;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.AWSLambdaException;
import com.amazonaws.SdkClientException;

import org.json.JSONObject;

import autoscaler.AutoScaler;


public class LoadBalancer {

    private static AutoScaler autoScaler;
    private static String AWS_REGION = "us-east-1";
    private static AmazonDynamoDB dynamoDB;
    private static final int PORT = 8000;

    private static List<MetricsEntry> compressImageMetrics;
    private static List<MetricsEntry> simulateMetrics;
    private static List<MetricsEntry> insectWarMetrics;

    public LoadBalancer(){}

    public static void main(String[] args) throws IOException, Exception {

        LoadBalancer loadBalancer = new LoadBalancer();
        loadBalancer.startServer();

        //Start Autoscaler
        autoScaler = new AutoScaler();
        Thread autoscalerThread = new Thread(() -> {
            try {
                AutoScaler.main(new String[] {});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        autoscalerThread.start();

        //Start DB
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
            .withCredentials(new EnvironmentVariableCredentialsProvider())
            .withRegion(AWS_REGION)
            .build();

        try {
            loadBalancer.createMetricsTable("compressimage");
            loadBalancer.createMetricsTable("simulate");
            loadBalancer.createMetricsTable("insectwar");
        } catch (Exception e) {
            // Handle the exception appropriately, such as logging an error or displaying a message
            e.printStackTrace();
        }

        Thread scanDbThread = new Thread(() -> {
            try {
                while (true) {
                    // Reset previous structures before retrieving new information
                    compressImageMetrics = new ArrayList<>();
                    simulateMetrics = new ArrayList<>();
                    insectWarMetrics = new ArrayList<>();

                    // Retrieve information from DynamoDB and store it in local structures
                    // retrieveAndStoreInformationFromDynamoDB();
                    // this will also calculate the complexity of each entry

                    loadBalancer.scanDB();

                    // Sleep for a specific duration before the next iteration
                    Thread.sleep(100000); // Sleep 
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        scanDbThread.start();

    }


    public void startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/compressimage", new CompressImageHandler());
        server.createContext("/", new RequestHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();

        System.out.println("Load Balancer started on port " + PORT);
    }

    public void createMetricsTable(String tableName) throws Exception{
        try{
            // Create a table with a primary hash key named 'id', which holds a string
            CreateTableRequest createTableRequest = new CreateTableRequest()
                            .withTableName(tableName)
                            .withKeySchema(new KeySchemaElement().withAttributeName("id").withKeyType(KeyType.HASH))
                            .withAttributeDefinitions(new AttributeDefinition().withAttributeName("id").withAttributeType(ScalarAttributeType.S))
                            .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

            // Create table if it does not exist yet
            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
            // wait for the table to move into ACTIVE state
            TableUtils.waitUntilActive(dynamoDB, tableName);              
        } catch (AmazonServiceException ase) {
                System.out.println("Caught an AmazonServiceException, which means your request made it "
                        + "to AWS, but was rejected with an error response for some reason.");
                System.out.println("Error Message:    " + ase.getMessage());
                System.out.println("HTTP Status Code: " + ase.getStatusCode());
                System.out.println("AWS Error Code:   " + ase.getErrorCode());
                System.out.println("Error Type:       " + ase.getErrorType());
                System.out.println("Request ID:       " + ase.getRequestId());
            }
    }

    public void scanDB(){

        // Scan the InsectWar table
        String insectWarTableName = "insectwar";
        ScanRequest insectWarScanRequest = new ScanRequest(insectWarTableName);
        ScanResult insectWarScanResult = dynamoDB.scan(insectWarScanRequest);
        List<Map<String, AttributeValue>> insectWarItems = insectWarScanResult.getItems();

        for (Map<String, AttributeValue> item : insectWarItems) {
            // Extract the attributes and create an InsectWarEntry object
            MetricsEntry entry = new MetricsEntry(
                Double.parseDouble(item.get("blocks").getS()),
                Double.parseDouble(item.get("methods").getS()),
                Double.parseDouble(item.get("instructions").getS()),
                Double.parseDouble(item.get("max").getS()),
                Double.parseDouble(item.get("army1").getS()),
                Double.parseDouble(item.get("army2").getS())
            );
            insectWarMetrics.add(entry);
        }

    
        String compressImageTableName = "compressimage";
        ScanRequest compressImageScanRequest = new ScanRequest(compressImageTableName);
        ScanResult compressImageScanResult = dynamoDB.scan(compressImageScanRequest);
        List<Map<String, AttributeValue>> compressImageItems = compressImageScanResult.getItems();

        for (Map<String, AttributeValue> item : compressImageItems) {
            MetricsEntry entry = new MetricsEntry(
                Double.parseDouble(item.get("blocks").getS()),
                Double.parseDouble(item.get("methods").getS()),
                Double.parseDouble(item.get("instructions").getS()),
                Double.parseDouble(item.get("imageWidth").getS()),
                Double.parseDouble(item.get("imageHeight").getS()),
                Double.parseDouble(item.get("compressionQuality").getS()),
                item.get("targetFormat").getS()
            );
            compressImageMetrics.add(entry);
        }

        String simulateTableName = "simulate";
        ScanRequest simulateScanRequest = new ScanRequest(simulateTableName);
        ScanResult simulateScanResult = dynamoDB.scan(simulateScanRequest);
        List<Map<String, AttributeValue>> simulateItems = simulateScanResult.getItems();

        for (Map<String, AttributeValue> item : simulateItems) {
            MetricsEntry entry = new MetricsEntry(
                Double.parseDouble(item.get("blocks").getS()),
                Double.parseDouble(item.get("methods").getS()),
                Double.parseDouble(item.get("instructions").getS()),
                Double.parseDouble(item.get("generations").getS()),
                item.get("scenario").getS(),
                item.get("world").getS()
            );
            simulateMetrics.add(entry);
        }

        // System.out.println("insectwar");
        // for (MetricsEntry entry : insectWarMetrics) {
        //     System.out.println("Blocks: " + entry.nBlocks);
        //     System.out.println("Methods: " + entry.nMethods);
        //     System.out.println("Instructions: " + entry.nInstructions);
        //     System.out.println("Max: " + entry.max);
        //     System.out.println("Army1: " + entry.army1);
        //     System.out.println("Army2: " + entry.army2);
        //     System.out.println("Complexity: " + entry.complexity);
        //     System.out.println(); // Print a blank line between entries
        // }
        // System.out.println("compressimage");
        // for (MetricsEntry entry : compressImageMetrics) {
        //     System.out.println("Blocks: " + entry.nBlocks);
        //     System.out.println("Methods: " + entry.nMethods);
        //     System.out.println("Instructions: " + entry.nInstructions);
        //     System.out.println("Width: " + entry.width);
        //     System.out.println("Height: " + entry.height);
        //     System.out.println("TargetFormat: " + entry.targetFormat);
        //     System.out.println("Compression factor: " + entry.compressionFactor);
        //     System.out.println("Complexity: " + entry.complexity);
        //     System.out.println(); // Print a blank line between entries
        // }
        // System.out.println("simulate");
        // for (MetricsEntry entry : simulateMetrics) {
        //     System.out.println("Blocks: " + entry.nBlocks);
        //     System.out.println("Methods: " + entry.nMethods);
        //     System.out.println("Instructions: " + entry.nInstructions);
        //     System.out.println("World: " + entry.world);
        //     System.out.println("Generations: " + entry.generations);
        //     System.out.println("Scenario: " + entry.scenario);
        //     System.out.println("Complexity: " + entry.complexity);
        //     System.out.println(); // Print a blank line between entries
        // }
    }

    private class RequestHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {

        // Handling CORS
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        // Process request and parameters
        String pseudoUrl = "";
        URI requestedUri = exchange.getRequestURI();
        String query = requestedUri.getRawQuery();
        Map<String, String> parameters = queryToMap(query);
        String requestType = getRequestType(parameters);

        System.out.println("Query: " + query);
        System.out.println("Request Type: " + requestType);

        double requestCost = 0;
        boolean useLambda = false;
        MetricsEntry closestEntry;
        double closestDifference;

        switch (requestType) {
            case "simulation":
                // Handle simulation request
                int generationsURL = Integer.parseInt(parameters.get("generations"));
                int worldURL = Integer.parseInt(parameters.get("world"));
                int scenarioURL = Integer.parseInt(parameters.get("scenario"));
                pseudoUrl = "/simulate?generations=" + generationsURL + "&world=" + worldURL + "&scenario=" + scenarioURL;

                double generations = Double.parseDouble(parameters.get("generations"));
                double world = Double.parseDouble(parameters.get("world"));
                double scenario = Double.parseDouble(parameters.get("scenario"));

                closestEntry = null;
                closestDifference = Double.POSITIVE_INFINITY;

                //CALCULATE REQUEST COST

                for (MetricsEntry entry : simulateMetrics) {
                    double difference = Math.abs(entry.generations - generations) + Math.abs(Double.parseDouble(entry.world) - world) + Math.abs(Double.parseDouble(entry.scenario) - scenario);

                    if (closestEntry == null || difference < closestDifference) {
                        closestEntry = entry;
                        closestDifference = difference;
                    }

                    if (difference == 0) {
                        requestCost = entry.complexity;
                        break;
                    }
                    if (closestEntry != null) {
                        requestCost = closestEntry.complexity*0.85;
                    }
                }

                
                break;

            case "insectwar":
                // Handle insectwar request
                int maxURL = Integer.parseInt(parameters.get("max"));
                int army1URL = Integer.parseInt(parameters.get("army1"));
                int army2URL = Integer.parseInt(parameters.get("army2"));
                pseudoUrl = "/insectwar?max=" + maxURL + "&army1=" + army1URL + "&army2=" + army2URL;

                double max = Double.parseDouble(parameters.get("max"));
                double army1 = Double.parseDouble(parameters.get("army1"));
                double army2 = Double.parseDouble(parameters.get("army2"));

                closestEntry = null;
                closestDifference = Double.POSITIVE_INFINITY;

                //CALCULATE REQUEST COST

                for (MetricsEntry entry : insectWarMetrics) {
                    double difference = (entry.max - max) + (entry.army1 - army1) + (entry.army2 - army2);

                    if (closestEntry == null || Math.abs(difference) < closestDifference) {
                        closestEntry = entry;
                        closestDifference = difference;
                    }

                    if (difference == 0) {
                        requestCost = entry.complexity;
                        break;
                    }
                    if (closestEntry != null) {
                        if(difference < 0){
                            requestCost = closestEntry.complexity*0.85;
                        }
                        else if(difference >0){
                            requestCost = closestEntry.complexity*1.15;
                        }
                    }
                }
                break;
        }


        // Retrieve the instances from the autoscaler
        Map<String, Object[]> allInstances = AutoScaler.getRunningInstances();


        //TODO CHOOSE WHETHER TO SEND TO LAMBDA OR DO PREVIOUS CODE 

        // Case in which we use a lambda
        
        if(requestCost < 500000){
            System.out.println("Entrou lambda");
            String functionName;
            AWSLambda awsLambda = AWSLambdaClientBuilder.standard()
                    .withCredentials(new EnvironmentVariableCredentialsProvider())
                    .build();

            try {
                JSONObject json = new JSONObject();

                // Set parameter values
                if(requestType.equals("simulation")){
                    functionName = "simulate-lambda";
                    json.put("generations", parameters.get("generations"));
                    json.put("world", parameters.get("world"));
                    json.put("scenario", parameters.get("scenario"));

                    System.out.println("Entrou simulate");
                    // Get the JSON string
                    String jsonString = json.toString();

                    InvokeRequest request = new InvokeRequest()
                            .withFunctionName(functionName)
                            .withPayload(jsonString);

                    InvokeResult res = awsLambda.invoke(request);
                    String value = new String(res.getPayload().array());

                    // Handle the successful response
                    exchange.sendResponseHeaders(200, value.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(value.getBytes());
                    os.close();
                }
                else if(requestType.equals("insectwar")){
                    System.out.println("Entrou insectwar");
                    functionName = "insectwar-lambda";
                    json.put("max", parameters.get("max"));
                    json.put("army1", parameters.get("army1"));
                    json.put("army2", parameters.get("army2"));

                    // Get the JSON string
                    String jsonString = json.toString();

                    InvokeRequest request = new InvokeRequest()
                            .withFunctionName(functionName)
                            .withPayload(jsonString);

                    InvokeResult res = awsLambda.invoke(request);
                    String value = new String(res.getPayload().array());

                    // Handle the successful response
                    exchange.sendResponseHeaders(200, value.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(value.getBytes());
                    os.close();
                }

            } catch (AWSLambdaException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            } catch (SdkClientException e) {
                System.err.println("AWS service connection error: " + e.getMessage());
                System.exit(1);
            }

            awsLambda.shutdown();
        }
        

        else{
            // Case in which we select an instance
            // SELECT INSTANCE WITH LOWEST ACCUMULATED COST
            String selectedInstanceId = null;
            double minRequestsOccupation = Double.MAX_VALUE;

            // Iterate over all instances and find the one with the lowest CPU utilization
            for (Map.Entry<String, Object[]> entry : allInstances.entrySet()) {
                String instanceId = entry.getKey();
                Object[] instanceData = entry.getValue();

                // Check if the instance data is valid 
                if (instanceData != null && instanceData.length >= 1) {

                    double requestsOccupation = (double) instanceData[2];

                    // Update the selected instance if the accumulated cost is lower
                    if (requestsOccupation < minRequestsOccupation) {
                        selectedInstanceId = instanceId;
                        minRequestsOccupation = requestsOccupation;
                    }
                }
            }

            System.out.println("Requests occupation of the instance with id = "  + selectedInstanceId + ": " + minRequestsOccupation);


            //if we have no data request cost = 1000

            if(requestCost==0){
                requestCost = 1000;
            }

            // Retrieve the instance object from the map
            Object[] instanceData = allInstances.get(selectedInstanceId);
            System.out.println("Selected Instance cpu: " + instanceData[1]);
            System.out.println("Selected instance costs: " + instanceData[2]);

            if (instanceData != null && instanceData.length >= 1) {
                Instance instance = (Instance) instanceData[0];
                String ipAddress = instance.getPublicIpAddress();
                System.out.println("Instance IP " + ipAddress);
                int port = 8000; // Assuming the instance listens on port 8000

                // Redirect the request to the selected instance
                String url = "http://" + ipAddress + ":" + port + pseudoUrl;

                // Update Instances List with current request cost
                double currentCost = (double) instanceData[2];
                double updatedCost = currentCost + requestCost;
                instanceData[2] = updatedCost;
                allInstances.put(selectedInstanceId, instanceData);
                System.out.println("Cost before adding: " + currentCost);
                System.out.println("Cost after adding: " + updatedCost);

                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setRequestMethod("GET");
                    connection.connect();
                    System.out.println("Connected to: " + url);

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        System.out.println("OK Response");

                        // Handle the successful response
                        InputStream inputStream = connection.getInputStream();
                        String responseData = readResponseData(inputStream);
                        exchange.sendResponseHeaders(200, responseData.toString().length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseData.getBytes());
                        os.close();

                        // Update Instances List after the request is done processing
                        currentCost = (double) instanceData[2];
                        updatedCost = currentCost - requestCost;
                        instanceData[2] = updatedCost;
                        allInstances.put(selectedInstanceId, instanceData);
                        System.out.println("Cost after removing: " + updatedCost);

                    } else {
                        // Handle the failed response
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, 0);
                        exchange.getResponseBody().close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, 0);
                    exchange.getResponseBody().close();
                }
            } 
            else {
                // Handle instance not found
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, 0);
                exchange.getResponseBody().close();
            }
            }
        }
    }

        public String readResponseData(InputStream inputStream) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder responseData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseData.append(line);
            }
            reader.close();
            return responseData.toString();
        }

        public String getRequestType(Map<String, String> parameters) {
            if (parameters.containsKey("world")) {
                return "simulation";
            } else if (parameters.containsKey("max")) {
                return "insectwar";
            }
            return "";
        }

        public Map<String, String> queryToMap(String query) {
            if(query == null) {
                return null;
            }
            Map<String, String> result = new HashMap<>();
            for(String param : query.split("&")) {
                String[] entry = param.split("=");
                if(entry.length > 1) {
                    result.put(entry[0], entry[1]);
                }else{
                    result.put(entry[0], "");
                }
            }
            return result;
        }

    private class CompressImageHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Handling CORS
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        InputStream stream = exchange.getRequestBody();

        // Result syntax: targetFormat:<targetFormat>;compressionFactor:<factor>;data:image/<currentFormat>;base64,<encoded image>
        String result = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
        String[] resultSplits = result.split(",");
        String targetFormat = resultSplits[0].split(":")[1].split(";")[0];
        String compressionFactor = resultSplits[0].split(":")[2].split(";")[0];
        String inputEncoded = resultSplits[1];
        String width;
        String height;

        System.out.println("Target Format: " + targetFormat);
        System.out.println("Compression Factor: " + compressionFactor);

        byte[] decoded = Base64.getDecoder().decode(inputEncoded);
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
            BufferedImage bi = ImageIO.read(bais);
            width = Integer.toString(bi.getWidth());
            height = Integer.toString(bi.getHeight());
            System.out.println("Width: " + width);
            System.out.println("Height: " + height);
        } catch (IOException e) {
            System.out.println("Could not read image");
            System.out.println(e);
            return;
        }



        // Retrieve the instances from the autoscaler
        Map<String, Object[]> allInstances = AutoScaler.getRunningInstances();
        
        double requestCost = 0;

        MetricsEntry closestEntry = null;
        double closestDifference = Double.POSITIVE_INFINITY;

        //Calculate request cost

        for (MetricsEntry entry : compressImageMetrics) {
            double difference = Math.abs(entry.width - Double.parseDouble(width))
                    + Math.abs(entry.height - Double.parseDouble(height))
                    + Math.abs(entry.compressionFactor - Double.parseDouble(compressionFactor));

            // Handle target format comparison
            if (entry.targetFormat.equals(targetFormat)) {
                difference += 0; // No difference if target formats are equal
            } else {
                difference += 500; // Increase difference if target formats are different
            }

            if (closestEntry == null || difference < closestDifference) {
                closestEntry = entry;
                closestDifference = difference;
            }

            if (difference == 0) {
                requestCost = entry.complexity;
                break;
            }
            if (closestEntry != null) {
                requestCost = closestEntry.complexity*0.85;
            }
        }


        //TODO CHOOSE LAMBDA OR EXECUTE THE REST OF THE CODE

        

        // Case in which we use a lambda

        // Case in which we select an instance
        // SELECT INSTANCE WITH LOWEST ACCUMULATED COST
        String selectedInstanceId = null;
        double minRequestsOccupation = Double.MAX_VALUE;

        // Iterate over all instances and find the one with the lowest CPU utilization
        for (Map.Entry<String, Object[]> entry : allInstances.entrySet()) {
            String instanceId = entry.getKey();
            Object[] instanceData = entry.getValue();

            System.out.println("Entered loop");
            // Check if the instance data is valid 
            if (instanceData != null && instanceData.length >= 1) {

                double requestsOccupation = (double) instanceData[2];

                System.out.println("Requests Occupation: " + requestsOccupation);

                // Update the selected instance if the accumulated cost is lower
                if (requestsOccupation < minRequestsOccupation) {
                    System.out.println("Discovered a cost different than 0: " + requestsOccupation);
                    selectedInstanceId = instanceId;
                    minRequestsOccupation = requestsOccupation;
                }
            }
        }

        System.out.println("Requests occupation of the instance with id = "  + selectedInstanceId + ": " + minRequestsOccupation);


        // Retrieve the instance object from the map
        Object[] instanceData = allInstances.get(selectedInstanceId);
        System.out.println("Selected Instance cpu: " + instanceData[1]);
        System.out.println("Selected instance costs: " + instanceData[2]);

        if(requestCost==0){
            requestCost = 10000;
        }

        if (instanceData != null && instanceData.length > 1) {
            Instance instance = (Instance) instanceData[0];
            String ipAddress = instance.getPublicIpAddress();
            int port = 8000; // Assuming the instance listens on port 8000

            // Redirect the request to the selected instance
            String url = "http://" + ipAddress + ":" + port  + "/compressimage";

            System.out.println("URL: " + url);

            // Update Instances List with current request cost
            double currentCost = (double) instanceData[2];
            double updatedCost = currentCost + requestCost;
            instanceData[2] = updatedCost;
            allInstances.put(selectedInstanceId, instanceData);
            System.out.println("Cost before adding: " + currentCost);
            System.out.println("Cost after adding: " + updatedCost);


            // Create a new URLConnection to the desired destination
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            // Set the request headers and write the output data
            connection.setRequestProperty("Content-Type", "text/plain");
            connection.setRequestProperty("Content-Length", String.valueOf(result.length()));
            connection.getOutputStream().write(result.getBytes());

            // Get the response from the destination
            int responseCode = connection.getResponseCode();
            System.out.println("Response code: " + responseCode );
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("OK Response imagecompression");
                // Handle the successful response
                InputStream responseStream = connection.getInputStream();
                String responseData = readResponseData(responseStream);

                // Send the response back to the client
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseData.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseData.getBytes());
                os.close();

                // Update Instances List after the request is done processing
                currentCost = (double) instanceData[2];
                updatedCost = currentCost - requestCost;
                instanceData[2] = updatedCost;
                allInstances.put(selectedInstanceId, instanceData);
                System.out.println("Cost after removing: " + updatedCost);

            } else {
                // Handle the failed response
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, 0);
                exchange.getResponseBody().close();
            }
        }
    }

    public String readResponseData(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder responseData = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            responseData.append(line);
        }
        reader.close();
        return responseData.toString();
    }

        public static String getCompressionType(String targetFormat) {
            switch (targetFormat) {
                case "jpg":
                case "JPG":
                case "jpeg":
                case "JPEG":
                    return "JPEG";
                case "gif":
                case "GIF":
                    return "LZW";
                case "bmp": // supported types:
                case "BMP": // [BI_RGB, BI_RLE8, BI_RLE4, BI_BITFIELDS, BI_JPEG, BI_PNG]
                    return "BI_RGB";
                case "png":
                case "PNG":
                    return "Deflate";
                case "tiff": // supported types:
                case "TIFF": // [CCITT RLE, CCITT T.4, CCITT T.6, LZW, JPEG, ZLib, PackBits, Deflate, EXIF JPEG]
                    return "ZLib";
                default:
                    return null;
            }
        }
    }
    public class MetricsEntry {
        // Fields for compressimage table
        public double width;
        public double height;
        public double compressionFactor;
        public String targetFormat;

        // Fields for simulate table
        public double generations;
        public String scenario;
        public String world;

        // Fields for insectwar table
        public double max;
        public double army1;
        public double army2;

        public double nBlocks;
        public double nMethods;
        public double nInstructions;

        // Calculated field
        public double complexity;

        public MetricsEntry(double nBlocks, double nMethods, double nInstructions, double width, double height, double compressionFactor, String targetFormat) {
            this.width = width;
            this.height = height;
            this.compressionFactor = compressionFactor;
            this.targetFormat = targetFormat;
            this.nBlocks = nBlocks;
            this.nMethods = nMethods;
            this.nInstructions = nInstructions;
            this.calculateComplexity(nBlocks, nMethods, nInstructions);
        }

        public MetricsEntry(double nBlocks, double nMethods, double nInstructions, double generations, String scenario, String world) {
            this.generations = generations;
            this.scenario = scenario;
            this.world = world;
            this.nBlocks = nBlocks;
            this.nMethods = nMethods;
            this.nInstructions = nInstructions;
            this.calculateComplexity(nBlocks, nMethods, nInstructions);
        }

        public MetricsEntry(double nBlocks, double nMethods, double nInstructions, double max, double army1, double army2) {
            this.nBlocks = nBlocks;
            this.nMethods = nMethods;
            this.nInstructions = nInstructions;
            this.max = max;
            this.army1 = army1;
            this.army2 = army2;
            this.calculateComplexity(nBlocks, nMethods, nInstructions);
        }

        // Calculate the complexity based on the provided formula
        public void calculateComplexity(double nBlocks, double nMethods, double nInstructions) {
            this.complexity = 0.33 * nInstructions +  0.1* (nMethods + nBlocks);
        }
    }

}
