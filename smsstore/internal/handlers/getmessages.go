package handlers

import (
	"encoding/json"
	"net/http"
	"smsstore/internal/repository"
	"smsstore/pkg/models"

	"github.com/gorilla/mux"
)

func GetUserMessages(w http.ResponseWriter, r *http.Request) {
	pathVars := mux.Vars(r)
	userID := pathVars["user_id"]
	messages, err := repository.GetUserMessages(userID)
	if err != nil {
		http.Error(w, "Failed to retrieve messages", http.StatusInternalServerError)
		return
	}

	apiResponse := models.ApiResponse{
		UserID:   userID,
		Messages: messages,
		Count:    len(messages),
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(apiResponse)
}
