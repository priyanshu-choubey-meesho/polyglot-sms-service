package routes

import (
	"smsstore/internal/handlers"

	"github.com/gorilla/mux"
)

// SetupRoutes initializes and configures HTTP routes.
// Returns an error if route setup fails (unlikely but possible for future validation).
func SetupRoutes() (*mux.Router, error) {
	router := mux.NewRouter()
	router.HandleFunc("/v1/user/{user_id}/messages", handlers.GetUserMessages).Methods("GET")
	return router, nil
}