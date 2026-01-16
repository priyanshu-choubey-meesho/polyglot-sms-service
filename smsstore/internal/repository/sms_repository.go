package repository

import (
	"context"
	"smsstore/internal/db"
	"smsstore/pkg/models"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

func AddMessageToUser(phoneNumber string, message string, status string) error {
	client := db.GetClient()
	collection := client.Database("smsstore").Collection("smsdata")

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	filter := bson.M{"_id": phoneNumber}
	update := bson.M{
		"$push": bson.M{
			"messages": bson.M{
				"message": message,
				"status":  status,
			},
		},
	}

	// Upsert option creates the user if they don't exist
	opts := options.Update().SetUpsert(true)
	_, err := collection.UpdateOne(ctx, filter, update, opts)

	return err
}

func GetUserMessages(phoneNumber string) ([]models.MessageWithStatus, error) {
	client := db.GetClient()
	collection := client.Database("smsstore").Collection("smsdata")

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	filter := bson.M{"_id": phoneNumber}
	var userData models.UserData
	err := collection.FindOne(ctx, filter).Decode(&userData)
	if err != nil {
		if err == mongo.ErrNoDocuments {
			// User not found - return empty slice instead of error
			return []models.MessageWithStatus{}, nil
		}
		return nil, err
	}
	return userData.Messages, nil
}
