package loadbalancer;

// import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
// import software.amazon.awssdk.services.lambda.LambdaClient;
// import software.amazon.awssdk.services.lambda.model.InvokeRequest;
// import software.amazon.awssdk.core.SdkBytes;
// import software.amazon.awssdk.services.lambda.model.InvokeResponse;
// import software.amazon.awssdk.services.lambda.model.LambdaException;
// import software.amazon.awssdk.core.SdkClient;


public class InvokeLambda {

//     public static void invokeFunction(String requestType) {

//         String functionName = "simulation-lambda";
//         LambdaClient awsLambda = LambdaClient.builder().credentialsProvider(EnvironmentVariableCredentialsProvider.create()).build();
    
//     //    try {
//     //        String json = "{\"number\":\"10\"}";
//     //        SdkBytes payload = SdkBytes.fromUtf8String(json) ;

//     //        InvokeRequest request = InvokeRequest.builder().functionName(functionName).payload(payload).build();

//     //        InvokeResponse res = awsLambda.invoke(request);
//     //        String value = res.payload().asUtf8String() ;
//     //        System.out.println(value);

//     //    } catch(LambdaException e) {
//     //        System.err.println(e.getMessage());
//     //        System.exit(1);
//     //    }

//        awsLambda.close();
//    }
}
