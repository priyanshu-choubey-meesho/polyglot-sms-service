package config

import (
	"errors"
	"os"
	"strings"
)

type Config struct {
	MongoURI     string
	DBName       string
	KafkaBrokers []string
	KafkaTopic   string
	KafkaGroupID string
	ServerPort   string
}

func getenv(key string, fallback string) string {
	val, exists := os.LookupEnv(key)
	if !exists {
		return fallback
	}
	return val
}

// LoadConfig loads and validates application configuration from environment variables.
// Returns an error if any required configuration is missing or invalid.
func LoadConfig() (*Config, error) {
	brokersStr := getenv("KAFKA_BROKERS", "localhost:9092")
	brokers := strings.Split(brokersStr, ",")
	// Filter out empty broker strings
	var validBrokers []string
	for _, broker := range brokers {
		if trimmed := strings.TrimSpace(broker); trimmed != "" {
			validBrokers = append(validBrokers, trimmed)
		}
	}

	cfg := &Config{
		MongoURI:     getenv("MONGO_URI", "mongodb://localhost:27017"),
		DBName:       getenv("DB_NAME", "sms_db"),
		KafkaBrokers: validBrokers,
		KafkaTopic:   getenv("KAFKA_TOPIC", "sms_events"),
		KafkaGroupID: getenv("KAFKA_GROUP_ID", "sms-storage-group"),
		ServerPort:   getenv("SERVER_PORT", ":8080"),
	}

	// Validate required fields
	if err := cfg.Validate(); err != nil {
		return nil, err
	}

	return cfg, nil
}

// Validate checks that all required configuration fields are set and valid.
func (c *Config) Validate() error {
	if c.MongoURI == "" {
		return errors.New("MONGO_URI is required and cannot be empty")
	}
	if c.DBName == "" {
		return errors.New("DB_NAME is required and cannot be empty")
	}
	if len(c.KafkaBrokers) == 0 {
		return errors.New("at least one KAFKA_BROKERS is required")
	}
	if c.KafkaTopic == "" {
		return errors.New("KAFKA_TOPIC is required and cannot be empty")
	}
	if c.KafkaGroupID == "" {
		return errors.New("KAFKA_GROUP_ID is required and cannot be empty")
	}
	if c.ServerPort == "" {
		return errors.New("SERVER_PORT is required and cannot be empty")
	}
	return nil
}
