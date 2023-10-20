import * as React from 'react';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import { Box, useTheme, InputAdornment, Snackbar, Alert } from "@mui/material";
import { tokens } from "../theme";
import { useNavigate } from 'react-router-dom';
import { useState, useEffect } from "react";
import { getAsync, putAsync } from '../utils/utils';
import { useCookies } from "react-cookie";


export default function EditPortfolio({ portfolioId, small }) {
    const navigate = useNavigate();
    const theme = useTheme();
    const colors = tokens(theme.palette.mode);
    const [cookie, removeCookie] = useCookies(["accessToken"]);
    const [portfolioData, setPortfolioData] = useState(null);
    const [updatedName, setUpdatedName] = useState('');
    const [updatedDescription, setUpdatedDescription] = useState('');
    const [updatedCapital, setUpdatedCapital] = useState('');




    useEffect(() => {
        // Replace this with your actual data fetching logic
        // Example: fetch portfolio data using portfolioId
        // Fetch the portfolio data based on portfolioId
        async function fetchData() {
            const response = await getAsync('portfolios/' + portfolioId, cookie.accessToken);
            const data = await response.json();
            setPortfolioData(data);
            console.log(data);
        }
        fetchData();
    }, [portfolioId, portfolioData, updatedCapital, updatedName, updatedDescription, cookie.accessToken]);

    const [isErrorAlertOpen, setIsErrorAlertOpen] = useState(false);
    const [isSuccessAlertOpen, setIsSuccessAlertOpen] = useState(false);

    const [open, setOpen] = React.useState(false);

    const handleClickOpen = () => {
        setOpen(true);
    };

    const handleClose = () => {
        setOpen(false);
    };

    // error alert handler
    const handleOpenErrorAlert = () => {
        setIsErrorAlertOpen(true);
        setIsSuccessAlertOpen(false);
    };
    const handleCloseErrorAlert = () => {
        setIsErrorAlertOpen(false);
    };

    // success alert handler
    const handleOpenSuccessAlert = () => {
        setIsSuccessAlertOpen(true);
        setIsErrorAlertOpen(false);
        
    };
    const handleCloseSuccessAlert = () => {
        setIsSuccessAlertOpen(false);
    };



    const handleChanges = () => {
        // Gather the form data (e.g., portfolio name, description, capital)
        const formData = {
            name: portfolioData.name,
            description: portfolioData.description,
            totalCapital: portfolioData.totalCapital
        };

        // Check if the name field has been updated and add it to the formData
        if (updatedName !== portfolioData.name && updatedName !== '') {
            formData.name = updatedName;
        }

        // Check if the description field has been updated and add it to the formData
        if (updatedDescription !== portfolioData.description && updatedDescription !== '') {
            formData.description = updatedDescription;
        }

        // Check if the capital field has been updated and add it to the formData
        if (updatedCapital !== portfolioData.totalCapital && updatedCapital !== '') {
            formData.totalCapital = parseFloat(updatedCapital);
        }

        console.log(formData);

        async function editPortfolio() {
            const response = await putAsync('portfolios/' + portfolioId, formData, cookie.accessToken);
            if (response.ok) {
                handleOpenSuccessAlert();
                setOpen(false);
                setPortfolioData({ ...portfolioData, name: updatedName });
                return;
                // setTimeout(() => {
                //     navigate("/portfolio/" + portfolioId);
                // }, 1000);
                // window.location.reload();
                // fetchData();
            } else {
                handleOpenErrorAlert();
            }
        }
        editPortfolio();
    };

    return (
        <div>
            {/* Snackbar for error message */}
            <Snackbar
                open={isErrorAlertOpen}
                autoHideDuration={5000} // Adjust the duration as needed
                onClose={handleCloseErrorAlert}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            >
                <Alert
                    elevation={6}
                    variant="filled"
                    severity="error"
                    onClose={handleCloseErrorAlert}
                    sx={{ backgroundColor: colors.redAccent[600] }}
                >
                    Portfolio update failed!
                </Alert>
            </Snackbar>

            {/* Snackbar for success message */}
            <Snackbar
                open={isSuccessAlertOpen}
                autoHideDuration={5000} // Adjust the duration as needed
                onClose={handleCloseSuccessAlert}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            >
                <Alert
                    elevation={6}
                    variant="filled"
                    severity="success"
                    onClose={handleCloseSuccessAlert}
                    sx={{ backgroundColor: colors.greenAccent[600] }}
                >
                    Portfolio updated successfully!
                </Alert>
            </Snackbar>
            <EditOutlinedIcon onClick={handleClickOpen} sx={{ color: colors.greenAccent[600], fontSize: (small ? "20px" : "35px") }} />
            <Dialog open={open} onClose={handleClose}>
                <DialogTitle
                    sx={{
                        color: colors.greenAccent[600],
                        backgroundColor: colors.primary[400],
                        fontSize: "22px",
                        fontWeight: "bold"
                    }}
                >
                    Edit This Portfolio
                </DialogTitle>
                <DialogContent
                    sx={{ backgroundColor: colors.primary[400] }}>
                    {/* <DialogContentText>
                        To create a new portfolio, please enter the portfolio name, description/strategy, and the capital.
                    </DialogContentText> */}
                    <TextField
                        margin="dense"
                        id="name"
                        label="Portfolio Name"
                        type="text"
                        fullWidth
                        variant="standard"
                        sx={{ color: colors.grey[100] }}
                        defaultValue={portfolioData?.name}
                        onChange={(e) => setUpdatedName(e.target.value)}
                        InputProps={{
                            classes: {
                                notchedOutline: 'portfolio-name-outline',
                            },
                        }}
                    />

                    <TextField
                        margin="dense"
                        id="desc"
                        label="Portfolio Description (Strategy)"
                        type="text"
                        fullWidth
                        variant="standard"
                        sx={{ color: colors.grey[100] }}
                        defaultValue={portfolioData?.description}
                        onChange={(e) => setUpdatedDescription(e.target.value)}
                    />
                    <TextField
                        margin="dense"
                        id="capital"
                        label="Portfolio Capital"
                        type="number"
                        fullWidth
                        variant="standard"
                        sx={{ color: colors.grey[100] }}
                        defaultValue={portfolioData?.totalCapital}
                        onChange={(e) => setUpdatedCapital(e.target.value)}
                        InputProps={{
                            startAdornment: <InputAdornment position="start">$</InputAdornment>,
                        }}
                    />
                    <style jsx>{`
                        .portfolio-name-outline {
                            color: ${colors.greenAccent[800]} !important;
                            border-color: ${colors.greenAccent[800]} !important;
                        }

                    `}</style>
                </DialogContent>
                <DialogActions sx={{ backgroundColor: colors.primary[400], paddingBottom: "20px", paddingRight: "20px" }}>
                    <Button onClick={handleClose} sx={{ color: colors.grey[300], fontWeight: "bold" }}>Cancel</Button>
                    <Button type="submit" sx={{ backgroundColor: colors.blueAccent[700], color: colors.grey[100], fontWeight: "bold" }} onClick={handleChanges}>Confirm Changes</Button>
                </DialogActions>
            </Dialog>
        </div>
    );
}