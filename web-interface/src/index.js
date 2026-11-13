import React from 'react'

import 'bootstrap/dist/js/bootstrap.bundle.min.js'
import 'bootstrap/dist/css/bootstrap.css'
import './index.css'
import App from './App'
import ApiRoutes from './util/ApiRoutes'
import DisplayPage from './components/dashboards/screen/DisplayPage'
import {createRoot} from "react-dom/client";

const container = document.getElementById('root');
const root = createRoot(container);

// Displays authenticate with their own token and run outside of the session handling of the app.
const isDisplay = window.location.pathname.replace(/\/+$/, "") === ApiRoutes.DISPLAY;

root.render(isDisplay ? <DisplayPage /> : <App />);
