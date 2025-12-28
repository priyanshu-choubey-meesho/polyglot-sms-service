package models

type ApiResponse struct {
	UserID   string              `json:"user_id"`
	Messages []MessageWithStatus `json:"messages"`
	Count    int                 `json:"count"`
}
