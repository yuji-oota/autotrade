package autotrade.local.utility;

import java.nio.file.Path;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AccessControlList;

public class AwsS3ClientWrapper {

    private static String bucketName;
    private static String accessKey;
    private static String secretKey;

    static {
        bucketName = AutoTradeProperties.get("aws.s3.bucketName");
        accessKey = AutoTradeProperties.get("aws.s3.accessKey");
        secretKey = AutoTradeProperties.get("aws.s3.secretKey");
    }

    public static void upload(Path path) {

        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                .withRegion(Regions.AP_NORTHEAST_1)
                .build();

        AccessControlList acl = s3Client.getObjectAcl(bucketName,  path.getFileName().toString());
        s3Client.putObject(bucketName, path.getFileName().toString(), path.toFile());
        s3Client.setObjectAcl(bucketName, path.getFileName().toString(), acl);

    }

}
