package routes

import (
	"smsstore/internal/handlers"

	"github.com/gorilla/mux"
)
func SetupRoutes() *mux.Router{
	router := mux.NewRouter()
	router.HandleFunc("/v1/user/{user_id}/messages",handlers.GetUserMessages).Methods("GET")
	return router
}