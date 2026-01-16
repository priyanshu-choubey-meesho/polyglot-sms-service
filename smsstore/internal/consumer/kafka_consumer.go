package consumer

import (
	"context"
	"encoding/json"
	"log"
	"smsstore/internal/config"
	"smsstore/internal/repository"
	"smsstore/pkg/models"

	"github.com/segmentio/kafka-go"
)

// StartKafkaConsumer starts consuming messages from Kafka and stores them in MongoDB
func StartKafkaConsumer(cfg *config.Config) {
	log.Println("========================================")
	log.Println("Initializing Kafka Consumer")
	log.Println("========================================")
	log.Printf("Brokers: %v", cfg.KafkaBrokers)
	log.Printf("Topic: %s", cfg.KafkaTopic)
	log.Printf("Group ID: %s", cfg.KafkaGroupID)

	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:  cfg.KafkaBrokers,
		Topic:    cfg.KafkaTopic,
		GroupID:  cfg.KafkaGroupID,
		MinBytes: 10e3, // 10KB
		MaxBytes: 10e6, // 10MB
	})
	defer reader.Close()

	log.Printf("✓ Kafka consumer started successfully")
	log.Printf("✓ Listening for messages on topic '%s'...", cfg.KafkaTopic)
	log.Println("========================================")

	for {
		log.Println("[WAITING] Polling for new messages...")
		msg, err := reader.ReadMessage(context.Background())
		if err != nil {
			log.Printf("[ERROR] Failed to read Kafka message: %v", err)
			continue
		}

		log.Printf("[RECEIVED] New message from partition %d, offset %d", msg.Partition, msg.Offset)
		log.Printf("[RAW] Message: %s", string(msg.Value))

		var smsEvent models.SmsEvent
		if err := json.Unmarshal(msg.Value, &smsEvent); err != nil {
			log.Printf("[ERROR] Failed to unmarshal SMS event: %v", err)
			log.Printf("[ERROR] Raw payload: %s", string(msg.Value))
			continue
		}

		log.Printf("[PROCESSING] SMS Event - Phone: %s, Status: %s", smsEvent.PhoneNumber, smsEvent.Status)
		log.Printf("[PROCESSING] Message content: %s", smsEvent.Message)

		// Store message in MongoDB with status
		if err := repository.AddMessageToUser(smsEvent.PhoneNumber, smsEvent.Message, smsEvent.Status); err != nil {
			log.Printf("[ERROR] Failed to store message in MongoDB: %v", err)
			continue
		}

		log.Printf("[SUCCESS] ✓ Message stored for %s with status: %s", smsEvent.PhoneNumber, smsEvent.Status)
		log.Println("----------------------------------------")
	}
}
