import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import { useTheme, InputAdornment, Snackbar, Alert } from "@mui/material";
import { tokens } from "../theme";
import { useState, useEffect } from "react";
import { getAsync, putAsync } from '../utils/utils';
import { useCookies } from "react-cookie";
import loadingLight from "./lotties/loading_light.json"
import Lottie from 'lottie-react';


export default function EditPortfolio({ portfolioId, small }) {
    const theme = useTheme();
    const colors = tokens(theme.palette.mode);
    const [cookie] = useCookies();
    const [portfolioData, setPortfolioData] = useState(null);
    const [updatedName, setUpdatedName] = useState('');
    const [updatedDescription, setUpdatedDescription] = useState('');
    const [updatedCapital, setUpdatedCapital] = useState('');
    const [capitalError, setCapitalError] = useState(false);
    const [isNameEdited, setIsNameEdited] = useState(false);
    const [isDescriptionEdited, setIsDescriptionEdited] = useState(false);
    const [isCapitalEdited, setIsCapitalEdited] = useState(false);
    const [loading, setLoading] = useState(false);
    const [alert, setAlert] = useState({ open: false, type: '', message: '' });

    const showAlert = (type, message) => {
        setAlert({ open: true, type, message });
    };

    const closeAlert = () => {
        setAlert({ open: false, type: '', message: '' });
    };

    // Function to validate capital input
    const validateCapital = (value) => {
        const isValid = /^(0|[1-9]\d*)(\.\d+)?$/.test(value) && value !== '' && parseFloat(value) > 0;
        setCapitalError(!isValid);
        return isValid; // Returns true if valid, false otherwise
    };

    useEffect(() => {
        // Fetch the portfolio data based on portfolioId
        async function fetchData() {
            const response = await getAsync('portfolios/' + portfolioId, cookie.accessToken);
            const data = await response.json();
            setPortfolioData(data);
            setUpdatedCapital(data.totalCapital.toString());
        }

        fetchData();

    }, [portfolioId, updatedCapital]);

    // Update the capital onChange handler
    const handleCapitalChange = (e) => {
        const newCapital = e.target.value;
        setUpdatedCapital(newCapital);
        validateCapital(newCapital);
        setIsCapitalEdited(true); // You can still keep the 'edited' state if needed for other logic
    };

    const hasChanges = () => {
        return (
            (updatedName && updatedName !== portfolioData?.name) ||
            (updatedDescription && updatedDescription !== portfolioData?.description) ||
            (isCapitalEdited && !capitalError && updatedCapital !== portfolioData?.totalCapital)
        );
    };

    const [open, setOpen] = useState(false);

    const handleClickOpen = () => {
        setOpen(true);
    };
    const handleClose = () => {
        setOpen(false);
        setUpdatedCapital('');
        setUpdatedDescription('');
        setUpdatedName('');
        setIsNameEdited(false);
        setIsDescriptionEdited(false);
        setIsCapitalEdited(false);
    };

    const handleChanges = () => {
        // Gather the form data (e.g., portfolio name, description, capital)
        setLoading(true);
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
        setLoading(true);

        async function editPortfolio() {
            try {
                const response = await putAsync('portfolios/' + portfolioId, formData, cookie.accessToken);
                if (response.ok) {
                    setLoading(false);
                    showAlert('success', 'Porfolio is successfully edited!');
                    handleClose();
                } else {
                    throw response;
                }
            }
            catch (err) {
                setLoading(false);
                handleClose();
                err.json().then(errorDetails => {
                    const error_message = errorDetails.details?.split(":")[1];
                    showAlert('error', "Error:" + error_message);
                }).catch(jsonError => {
                    showAlert('error', "An error occurred");
                });
            }
        }
        editPortfolio();
    };

    return (
        <div>
            {/* Snackbar for error message */}
            <Snackbar
                open={alert.open && alert.type === 'error'}
                autoHideDuration={5000}
                onClose={closeAlert}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            >
                <Alert
                    elevation={6}
                    variant="filled"
                    severity="error"
                    onClose={closeAlert}
                    sx={{ backgroundColor: colors.redAccent[600] }}
                >
                    {alert.message}
                </Alert>
            </Snackbar>

            {/* Snackbar for success message */}
            <Snackbar
                open={alert.open && alert.type === 'success'}
                autoHideDuration={5000}
                onClose={closeAlert}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            >
                <Alert
                    elevation={6}
                    variant="filled"
                    severity="success"
                    onClose={closeAlert}
                    sx={{ backgroundColor: colors.greenAccent[600] }}
                >
                    {alert.message}
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
                    <TextField
                        margin="dense"
                        id="name"
                        label="Portfolio Name"
                        type="text"
                        fullWidth
                        variant="standard"
                        sx={{ color: colors.grey[100] }}
                        defaultValue={portfolioData?.name}
                        onInput={(e) => { setUpdatedName(e.target.value); setIsNameEdited(true) }}
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
                        onChange={(e) => { setUpdatedDescription(e.target.value); setIsDescriptionEdited(true) }}
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
                        onChange={handleCapitalChange}
                        InputProps={{
                            startAdornment: <InputAdornment position="start">$</InputAdornment>,
                        }}
                        error={capitalError && isCapitalEdited} // Show error only if edited
                        helperText={capitalError && isCapitalEdited ? 'Invalid capital value' : ''}
                    />
                    <style>
                        {`
                        .portfolio-name-outline {
                            color: ${colors.greenAccent[800]} !important;
                            border-color: ${colors.greenAccent[800]} !important;
                        }

                    `}</style>
                </DialogContent>
                <DialogActions
                    sx={{ backgroundColor: colors.primary[400], paddingBottom: "30px", paddingRight: "20px" }}
                >
                    <Button
                        onClick={handleClose}
                        sx={{ color: colors.grey[300], fontWeight: "bold" }}
                    >
                        Cancel
                    </Button>
                    <Button
                        type="submit"
                        sx={{ backgroundColor: loading ? colors.greenAccent[600] : colors.blueAccent[700], color: colors.grey[100], fontWeight: "bold", width: "150px", height: "40px", "&:hover": { backgroundColor: colors.greenAccent[600] } }}
                        onClick={handleChanges}
                        disabled={!hasChanges() || loading || capitalError}
                    >
                        {loading ?
                            <Lottie
                                animationData={loadingLight}
                                loop={true}
                                autoplay={true}
                                style={{ width: '80px', height: '80px' }}
                            />
                            :
                            "Confirm Changes"
                        }
                    </Button>
                </DialogActions>
            </Dialog>
        </div>
    );
}