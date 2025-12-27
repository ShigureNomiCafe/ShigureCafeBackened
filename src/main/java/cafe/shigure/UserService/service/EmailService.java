package cafe.shigure.UserService.service;

import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final GraphServiceClient graphClient;

    @Value("${application.mail.from}")
    private String fromEmail;

    public void sendSimpleMessage(String to, String subject, String text) {
        Message message = new Message();
        message.setSubject(subject);

        // 设置邮件内容
        ItemBody body = new ItemBody();
        body.setContentType(BodyType.Text);
        body.setContent(text);
        message.setBody(body);

        // 设置收件人
        Recipient recipient = new Recipient();
        EmailAddress emailAddress = new EmailAddress();
        emailAddress.setAddress(to);
        recipient.setEmailAddress(emailAddress);
        message.setToRecipients(Collections.singletonList(recipient));

        // 构建发送请求
        SendMailPostRequestBody sendMailPostRequestBody = new SendMailPostRequestBody();
        sendMailPostRequestBody.setMessage(message);
        sendMailPostRequestBody.setSaveToSentItems(true);

        // 直接通过 noreply 邮箱发送
        graphClient.users().byUserId(fromEmail).sendMail().post(sendMailPostRequestBody);
    }
}