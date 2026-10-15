FROM node:20-alpine

WORKDIR /app

# Install dependencies
COPY package.json package-lock.json ./
RUN npm install

# Copy source code
COPY . .

EXPOSE 5173

# Start Vite dev server on 0.0.0.0
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]

