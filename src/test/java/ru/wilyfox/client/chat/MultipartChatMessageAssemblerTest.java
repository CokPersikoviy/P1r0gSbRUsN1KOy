package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MultipartChatMessageAssemblerTest {
    private static MultipartChatMessageAssembler assembler() {
        return new MultipartChatMessageAssembler(2, 3, 5, 10, 1_000L);
    }

    @Test
    void completesPartsFromOneSender() {
        MultipartChatMessageAssembler assembler = assembler();

        assertEquals(MultipartChatMessageAssembler.Status.ACCEPTED,
                assembler.accept("Fox", "one", 2, 2, "world", 100L).status());
        MultipartChatMessageAssembler.Result result = assembler.accept("Fox", "one", 1, 2, "hello", 200L);

        assertEquals(MultipartChatMessageAssembler.Status.COMPLETE, result.status());
        assertEquals("helloworld", result.payload());
        assertEquals(0, assembler.size());
    }

    @Test
    void rejectsSecondMessageForSameSenderUntilFirstExpires() {
        MultipartChatMessageAssembler assembler = assembler();

        assembler.accept("Fox", "one", 1, 2, "a", 100L);
        MultipartChatMessageAssembler.Result rejected = assembler.accept("Fox", "two", 1, 1, "b", 200L);
        MultipartChatMessageAssembler.Result acceptedAfterTtl = assembler.accept("Fox", "two", 1, 1, "b", 1_101L);

        assertEquals(MultipartChatMessageAssembler.Status.REJECTED, rejected.status());
        assertNull(rejected.payload());
        assertEquals(MultipartChatMessageAssembler.Status.COMPLETE, acceptedAfterTtl.status());
    }

    @Test
    void enforcesPartSenderAndAggregateLimits() {
        MultipartChatMessageAssembler assembler = assembler();

        assertEquals(MultipartChatMessageAssembler.Status.REJECTED,
                assembler.accept("Fox", "large", 1, 4, "a", 0L).status());
        assertEquals(MultipartChatMessageAssembler.Status.REJECTED,
                assembler.accept(null, "none", 1, 1, "a", 0L).status());

        assembler.accept("Fox", "aggregate", 1, 3, "12345", 0L);
        assembler.accept("Fox", "aggregate", 2, 3, "67890", 0L);
        assertEquals(MultipartChatMessageAssembler.Status.REJECTED,
                assembler.accept("Fox", "aggregate", 3, 3, "x", 0L).status());
    }

    @Test
    void limitsConcurrentSenders() {
        MultipartChatMessageAssembler assembler = assembler();

        assembler.accept("One", "a", 1, 2, "a", 0L);
        assembler.accept("Two", "b", 1, 2, "b", 0L);

        assertEquals(MultipartChatMessageAssembler.Status.REJECTED,
                assembler.accept("Three", "c", 1, 2, "c", 0L).status());
    }
}
