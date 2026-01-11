# Collaborative Editing System - Frontend

A modern React-based web application for collaborative document editing.

## Features

- **User Authentication**: Register and login functionality
- **Document Management**: Create, edit, and delete documents
- **Real-time Collaboration**: Multiple users can edit documents simultaneously using WebSocket
- **Version Control**: View version history and revert to previous versions
- **Collaborator Management**: Add and remove collaborators from documents

## Prerequisites

- Node.js (v16 or higher)
- npm or yarn

## Installation

1. Navigate to the frontend directory:
```bash
cd frontend
```

2. Install dependencies:
```bash
npm install
```

## Running the Application

Start the development server:
```bash
npm run dev
```

The application will be available at `http://localhost:3000`

## Building for Production

Build the application:
```bash
npm run build
```

The built files will be in the `dist` directory.

## Configuration

The frontend is configured to connect to:
- API Gateway: `http://localhost:8080`
- WebSocket Server: `ws://localhost:8082`

These can be changed in `vite.config.js` if needed.

## Project Structure

```
frontend/
├── src/
│   ├── components/
│   │   ├── Auth/          # Login and Register components
│   │   ├── Dashboard/      # Document list and management
│   │   └── Document/       # Document editor with real-time collaboration
│   ├── services/           # API service layer
│   │   ├── api.js          # Axios configuration
│   │   ├── authService.js  # Authentication API calls
│   │   ├── documentService.js  # Document API calls
│   │   ├── versionService.js   # Version control API calls
│   │   └── websocketService.js # WebSocket connection management
│   ├── App.jsx             # Main application component
│   └── main.jsx            # Application entry point
├── package.json
└── vite.config.js          # Vite configuration
```

## Usage

1. **Register/Login**: Create an account or login with existing credentials
2. **Create Document**: Click "Create New Document" on the dashboard
3. **Edit Document**: Click on any document to open the editor
4. **Collaborate**: Share the document URL with others (they need to be added as collaborators)
5. **Version History**: Click "Version History" to view and revert to previous versions
6. **Add Collaborators**: Click "Collaborators" to manage document access

## Notes

- The WebSocket connection is established automatically when opening a document
- Changes are auto-saved every 3 seconds
- Real-time updates from other users appear automatically

