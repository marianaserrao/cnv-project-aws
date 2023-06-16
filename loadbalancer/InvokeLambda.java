package loadbalancer;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.AWSLambdaException;
import com.amazonaws.SdkClientException;

public class InvokeLambda {

    public static void invokeFunction(String requestType) {

        String functionName = "simulation-lambda";
        AWSLambda awsLambda = AWSLambdaClientBuilder.standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .build();

        try {
            String json = "{\"number\":\"10\"}";

            InvokeRequest request = new InvokeRequest()
                    .withFunctionName(functionName)
                    .withPayload(json);

            InvokeResult res = awsLambda.invoke(request);
            String value = new String(res.getPayload().array());
            System.out.println(value);

        } catch (AWSLambdaException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (SdkClientException e) {
            System.err.println("AWS service connection error: " + e.getMessage());
            System.exit(1);
        }

        awsLambda.shutdown();
    }
}
