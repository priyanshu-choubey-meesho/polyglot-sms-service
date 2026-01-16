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
	once        sync.Once
)

// GetClient returns a singleton MongoDB client initialized with app config.
func GetClient() *mongo.Client {
	once.Do(func() {
		var err error
		cfg := config.LoadConfig()
		mongoClient, err = connectDB(cfg.MongoURI)
		if err != nil {
			log.Fatal("Failed to connect to MongoDB:", err)
		}
	})
	return mongoClient
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
