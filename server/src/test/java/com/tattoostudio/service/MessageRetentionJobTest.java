package com.tattoostudio.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.tattoostudio.model.Message;
import com.tattoostudio.support.IntegrationTest;

class MessageRetentionJobTest extends IntegrationTest {

    @Autowired
    MessageRetentionJob job;

    @Test
    void messagesOlderThan90DaysAreDeleted() {
        messages.save(message("old", Duration.ofDays(91)));
        messages.save(message("recent", Duration.ofDays(89)));

        job.run();

        assertThat(messages.findAll()).extracting(Message::getName).containsExactly("recent");
    }

    private Message message(String name, Duration age) {
        Message m = new Message();
        m.setName(name);
        m.setContact("0888123456");
        m.setText("Hello");
        m.setCreatedAt(NOW.minus(age));
        return m;
    }
}
