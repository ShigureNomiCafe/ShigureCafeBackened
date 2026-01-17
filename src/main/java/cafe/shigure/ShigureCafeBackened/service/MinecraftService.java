package cafe.shigure.ShigureCafeBackened.service;

import cafe.shigure.ShigureCafeBackened.dto.ChatMessageRequest;
import cafe.shigure.ShigureCafeBackened.model.ChatMessage;
import cafe.shigure.ShigureCafeBackened.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MinecraftService {

    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public void saveChatMessage(ChatMessageRequest request) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setName(request.name());
        chatMessage.setMessage(request.message());
        chatMessageRepository.save(chatMessage);
    }
}
