FROM ollama/ollama:latest

# Pull the model during build so it's ready when container starts
RUN ollama serve & sleep 5 && ollama pull qwen2.5:7b && pkill ollama

EXPOSE 11434
ENTRYPOINT ["ollama", "serve"]