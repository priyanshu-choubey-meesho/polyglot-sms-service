package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"smsstore/internal/config"
	"smsstore/internal/consumer"
	"smsstore/internal/db"
	"smsstore/internal/routes"
	"syscall"
	"time"
)

func main() {
	// Configure logger to write unbuffered output directly to stdout
	// This ensures logs are written immediately without buffering
	log.SetOutput(os.Stdout)
	log.SetFlags(log.LstdFlags | log.Lmicroseconds)

	db.GetClient()

	cfg := config.LoadConfig()

	router := routes.SetupRoutes()

	server := &http.Server{
		Addr:         cfg.ServerPort,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		log.Printf("HTTP server listening on %s", cfg.ServerPort)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Could not listen on %s: %v\n", cfg.ServerPort, err)
		}
	}()

	// Start Kafka consumer in goroutine
	go consumer.StartKafkaConsumer(cfg)

	// Setup graceful shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down server...")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		log.Fatal("Server forced to shutdown:", err)
	}

	if err := db.DisconnectMongo(); err != nil {
		log.Println("Error disconnecting MongoDB:", err)
	}

	log.Println("Server exited")
}
