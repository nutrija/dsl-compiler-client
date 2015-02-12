package com.dslplatform.compiler.client.parameters;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.rds.AmazonRDSAsyncClient;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.*;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.dslplatform.compiler.client.*;

import java.util.concurrent.Future;

public enum AwsDeploy implements CompileParameter {
    INSTANCE;

    @Override
    public String getAlias() { return "aws"; }

    @Override
    public String getUsage() { return null; }

    @Override
    public boolean check(Context context) throws ExitException {
        return true;
    }

    @Override
    public void run(Context context) throws ExitException {

        DBInstance db = launchRDSInstance(context);
        String connString = String.format("%s/revenj?user=revenj&password=revenj123abc",
                db.getEndpoint());/*,
                db.getDBName(),(
                db.getMasterUsername(),
                db.get);*/
        context.put(DbConnection.INSTANCE, connString);
        try {
            Migration.INSTANCE.run(context);
        } catch (ExitException e) {
            e.printStackTrace();
        }

        // deployRevenj(connString);
    }

    @Override
    public String getShortDescription() {
        return "Deploy to Amazon Web Services";
    }

    @Override
    public String getDetailedDescription() {
        return "Deploy Revenj http server and Postgres to AWS.\n"+
                "1. Launches a new RDB instance and applies migration.\n"+
                "2. Launches a Container instance with Revenj running in Docker container";
    }

    private boolean validateCredentials(AWSCredentials credentials) {
        try {
            AmazonS3Client client = new AmazonS3Client(credentials);
            // @todo should be some dummy request
            client.getS3AccountOwner();
            return true;
        } catch (AmazonS3Exception e) {
            return false;
        }
    }

    private AWSCredentials getValidCredentials(Context context) {
        AWSCredentials credentials;
        AWSCredentialsProvider provider = new DefaultAWSCredentialsProviderChain();
        try {
            credentials = provider.getCredentials();
            if (validateCredentials(credentials))
                return credentials;
            context.log("Invalid system credentials");
        } catch (AmazonClientException e) {
            context.log("System credentials not found");
        }
        String accessKey = context.load("AWS_ACCESS_KEY_ID");
        String secretKey = context.load("AWS_SECRET_KEY");
        if (accessKey != null && secretKey != null) {
            credentials = new BasicAWSCredentials(accessKey, secretKey);
            if (validateCredentials(credentials))
                return credentials;
            context.log("Invalid cached credentials");
        }
        do {
            accessKey = context.ask("Enter your AWS access key id: ");
            secretKey = context.ask("Enter your AWS secret key: ");
            credentials = new BasicAWSCredentials(accessKey, secretKey);
        } while (!validateCredentials(credentials));

        context.cache("AWS_ACCESS_KEY_ID", accessKey);
        context.cache("AWS_SECRET_KEY", secretKey);

        return credentials;
    }

    private Regions getRegion(Context context) {
        String cachedRegion = context.load("AWS_REGION");
        String defaultRegion = cachedRegion != null ? cachedRegion : Regions.DEFAULT_REGION.getName();
        while (true) {
            try {
                String answer = context.ask(String.format("Enter region [%s]", defaultRegion));
                if (answer.isEmpty())
                    answer = defaultRegion;
                Regions region = Regions.fromName(answer);
                return region;
            } catch (IllegalArgumentException e) {
                context.error("Invalid region");
            }
        }
    }

    public DBInstance launchRDSInstance(Context context) throws ExitException {

        final String instanceId = context.ask("Enter database instance ID");

        // @todo ask user for options
        CreateDBInstanceRequest createRequest = new CreateDBInstanceRequest()
                .withAllocatedStorage(5) // 5GB is minimum
                .withPort(5432)
                .withDBName("revenj")
                .withMasterUsername("revenj")
                .withMasterUserPassword("revenj123abc")
                .withEngine("postgres")
                .withEngineVersion("9.3.3")
                .withStorageType("standard")
                .withDBInstanceClass("db.t1.micro")
                .withDBInstanceIdentifier(instanceId)
                ;

        // TODO make sure db is publicly accessible
        // 1) create appropriate security group
        // 2) a) running VPC (default, or when starting in new region) -> add VPC security group
        //    .withVpcSecurityGroupIds("group-name")
        //    b) no-VPC
        //    .withDBSecurityGroups("group-name")
        //  security group example: allow inbound TCP traffic: 0.0.0.0/0 5432

        AmazonRDSClient client = new AmazonRDSClient(getValidCredentials(context))
                .withRegion(getRegion(context));

        context.log("Sending create-db-instance request");

        try {
            DBInstance db = client.createDBInstance(createRequest);
        } catch (DBInstanceAlreadyExistsException e) {
            context.error(String.format("Database instance '%s' already exists", instanceId));
        }

        while (true) {
            // poll status until database instance to be created
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                context.error(e);
                throw new ExitException();
            }
            DescribeDBInstancesRequest describeRequest = new DescribeDBInstancesRequest()
                    .withDBInstanceIdentifier(instanceId);
            DescribeDBInstancesResult describeResponse = client.describeDBInstances(describeRequest);
            DBInstance db = describeResponse.getDBInstances().get(0);

            if (!db.getDBInstanceStatus().contains("Creating")) {
                context.show("Database created");
                break;
            }
            context.show("Status: " + db.getDBInstanceStatus());
        };

        return null;
    }

    public void launchEC2Instance

    public void deployRevenj(Context context)
    {
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

        AmazonEC2Client client = new AmazonEC2Client(getValidCredentials(context));

        CreateSecurityGroupRequest groupRequest = new CreateSecurityGroupRequest()
                .withGroupName("revenj-security-group").withDescription("Revenj security group");
        CreateSecurityGroupResult createSecurityGroupResult = client.createSecurityGroup(groupRequest);

        String imageId = "ami-x";
        runInstancesRequest.withImageId(imageId)
                .withInstanceType("m1.micro")
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName("key-pair")
                .withSecurityGroups("security-group")

    }
}
