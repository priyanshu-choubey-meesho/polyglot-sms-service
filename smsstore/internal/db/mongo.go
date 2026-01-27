package db

import (
	"context"
	"log"
	"smsstore/internal/config"
	"sync"
	"time"

	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

var (
	mongoClient *mongo.Client
	initErr     error
	once        sync.Once
)

// GetClient returns a singleton MongoDB client initialized with app config.
// Returns an error if the connection failed during initialization.
// Subsequent calls will return the same error if initialization failed.
func GetClient() (*mongo.Client, error) {
	once.Do(func() {
		cfg, err := config.LoadConfig()
		if err != nil {
			initErr = err
			mongoClient = nil
			return
		}
		mongoClient, initErr = connectDB(cfg.MongoURI)
		if initErr != nil {
			log.Printf("Failed to connect to MongoDB: %v", initErr)
			mongoClient = nil
		}
	})
	return mongoClient, initErr
}

func connectDB(uri string) (*mongo.Client, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	clientOptions := options.Client().ApplyURI(uri)

	client, err := mongo.Connect(ctx, clientOptions)
	if err != nil {
		return nil, err
	}

	if err := client.Ping(ctx, nil); err != nil {
		return nil, err
	}

	log.Println("MongoDB connected successfully")
	return client, nil
}

// DisconnectMongo gracefully closes the shared client.
func DisconnectMongo() error {
	if mongoClient == nil {
		return nil
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	err := mongoClient.Disconnect(ctx)
	if err != nil {
		log.Printf("Error disconnecting from MongoDB: %v", err)
		return err
	}
	log.Println("MongoDB disconnected successfully")
	return nil
}
