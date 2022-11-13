package pro.sky.telegrambot.listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.entity.NotificationTask;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
@Service
public class TelegramBotUpdatesListener implements UpdatesListener {
    private final Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);
    private final NotificationTaskRepository notificationTaskRepository;
    private static final Pattern pattern = Pattern.compile("([0-9.:\\s]{16})(\\s)([\\W+]+)");

    private final TelegramBot telegramBot;

    public TelegramBotUpdatesListener(NotificationTaskRepository notificationTaskRepository, TelegramBot telegramBot) {
        this.notificationTaskRepository = notificationTaskRepository;
        this.telegramBot = telegramBot;
    }

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    @Scheduled(cron = "0 0/1 * * * *")
    public void sendNotification() {
        List<NotificationTask> sentNotifications = notificationTaskRepository
                .findAllByDateTime(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        sentNotifications.forEach(notificationTask -> {
            long chatId = notificationTask.getChatId();
            String message = notificationTask.getNotification();
            if (!message.isEmpty()) {
                SendResponse sendNotification = telegramBot.execute(new SendMessage(chatId,
                        "new notification - " + message));
                logger.info("notification - {}, chatId - {}", message, chatId);
                answer(sendNotification);
            }
        });
    }

    @Override
    public int process(List<Update> updates) {
        try {
            updates.forEach(update -> {
                logger.info("Processing update: {}", update);
                // Process your updates here
                Long chatId = update.message().chat().id();
                String incomingMessage = update.message().text();
                if (incomingMessage.equals("/start")) {
                    SendResponse response = telegramBot.execute(new SendMessage(chatId, "Let's get it started!" + "\n" +
                            "Write a notification in the format: <dd.MM.yyyy HH:mm Notification text> without quotes and brackets" +
                            "and I will send you a reminder at the specified time"));
                    answer(response);
                } else {
                    try {
                        createNotification(update);
                        SendResponse response = telegramBot.execute(new SendMessage(chatId, "Your notification saved"));
                        answer(response);
                        logger.info("Notification saved");
                    } catch (DataFormatException e) {
                        logger.warn("Notification unsaved");
                        SendResponse response = telegramBot.execute
                                (new SendMessage(chatId,
                                        "Incorrect notification text. " +
                                                "Enter the notification in the format <dd.MM.yyyy HH:mm Notification text> without quotes and brackets"));
                        answer(response);
                    }
                }

            });
        } finally {
            {
                return UpdatesListener.CONFIRMED_UPDATES_ALL;
            }
        }
    }

    private void answer(SendResponse response) {
        if (!response.isOk()) {
            logger.warn("Response error code is: {}", response.errorCode());
        } else {
            logger.info("Response is: {}", response.isOk());
        }
    }

    public List<String> requestParsing(String text) throws DataFormatException {
        Matcher matcher = pattern.matcher(text);
        if (matcher.matches()) {
            String dateTime = matcher.group(1);
            String notification = matcher.group(3);
            return List.of(dateTime, notification);
        } else {
            logger.warn("Incorrect data format");
            throw new DataFormatException("Incorrect data format");
        }
    }

    public void createNotification(Update update) throws DataFormatException {
        String message = update.message().text();
        Long chatId = update.message().chat().id();
        List<String> timeAndText = new ArrayList<>(requestParsing(message));
        String time = timeAndText.get(0);
        String text = timeAndText.get(1);
        LocalDateTime localDateTime = LocalDateTime.parse(time, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        NotificationTask notificationTask = new NotificationTask(chatId, text, localDateTime);
        notificationTaskRepository.save(notificationTask);
        logger.info("Notification save {}", notificationTask);
    }

}
