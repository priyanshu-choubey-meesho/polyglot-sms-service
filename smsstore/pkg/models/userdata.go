package models

type MessageWithStatus struct {
	Message string `bson:"message" json:"message"`
	Status  string `bson:"status" json:"status"`
}

type UserData struct {
	ID       string              `bson:"_id" json:"id"`
	Messages []MessageWithStatus `bson:"messages" json:"messages"`
}
