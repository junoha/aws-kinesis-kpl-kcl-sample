package com.junoha.sample.kinesis.producer;

import com.amazonaws.services.kinesis.producer.UserRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import com.junoha.sample.kinesis.producer.model.Person;
import com.junoha.sample.kinesis.producer.model.Pet;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DataGenerator {
    private DataGenerator() {
    }

    private static Faker faker;
    private static ObjectMapper mapper = new ObjectMapper();

    private static Faker getFakerInstance() {
        if (faker == null) {
            // faker = new Faker(new Locale.Builder().setLocale(Locale.JAPAN).build());
            faker = new Faker(new Locale.Builder().setLocale(Locale.US).build());
        }
        return faker;
    }

    private static Person getPerson() {
        /**
         * Generate Fake Data with Faker
         * https://github.com/DiUS/java-faker
         *
         * Example
         * {
         *   "id": 54207,
         *   "date": "2019/09/28 15:36:46",
         *   "score": 6315932014,
         *   "firstName": "Doyle",
         *   "lastName": "Goldner",
         *   "address": "Apt. 339 84193 Lockman Parkway, Jerdeshire, WI 17484",
         *   "pets": [
         *     {
         *       "type": "turtle ",
         *       "name": "Donatello"
         *     },
         *     {
         *       "type": "snail",
         *       "name": "El Greco"
         *     }
         *   ]
         * }
         */
        Faker f = getFakerInstance();
        Person person = new Person();
        person.setId(f.number().randomNumber(5, false));
        person.setDate(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(LocalDateTime.now()));
        person.setScore(f.number().randomNumber(10, false));
        person.setAddress(f.address().fullAddress());
        person.setFirstName(f.name().firstName());
        person.setLastName(f.name().lastName());

        List<Pet> pets = IntStream.range(0, f.number().numberBetween(0, 10)).mapToObj(i -> {
            Pet pet = new Pet();
            pet.setType(f.animal().name());
            pet.setName(f.artist().name());
            return pet;
        }).collect(Collectors.toList());
        person.setPets(pets);

        return person;
    }

    private static String generateDummyData() {
        Person person = getPerson();
        try {
            // Object to JSON String
            return mapper.writeValueAsString(person);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    protected static UserRecord generateUserRecord(String streamName) {
        try {
            // Partition key is timestamp mills
            return new UserRecord()
                    .withStreamName(streamName)
                    .withPartitionKey(Long.toString(System.currentTimeMillis()))
                    .withData(ByteBuffer.wrap(generateDummyData().getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
