import React from 'react';
import {
  createBrowserRouter,
  RouterProvider,
} from "react-router-dom";
import AdminRootPage from './pages/admin/AdminRootPage';
import AllInstancesPage from './pages/admin/all-instances';
import ApprovedInstancesPage from './pages/admin/approved-instances-page';

const router = createBrowserRouter([
  {
    path: "/",
    element: <div>Home</div>
  },
  {
    path: "/admin",
    element: <AdminRootPage />,
    errorElement: <div>Not Found</div>,
    children: [
      {
        path: "",
        element: <div>Hoge</div>
      },
      {
        path: "all-instances",
        element: <AllInstancesPage />
      },
      {
        path: "approved-instances",
        element: <ApprovedInstancesPage />
      },
      {
        path: "unapproved-instances",
        element: <div>Unapproved</div>
      },
      {
        path: "blacklist",
        element: <div>Blacklist</div>
      },
      {
        path: "account",
        element: <div>Account</div>
      }
    ]
  }
]);


function App() {
  return (
    <RouterProvider router={router} />
  );
}

export default App;
