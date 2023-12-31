import { useState } from 'react';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import { Box, Typography, useTheme, Snackbar, Alert } from "@mui/material";
import { tokens } from "../theme";
import AddCircleOutlineIcon from '@mui/icons-material/AddCircleOutline';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import StockSelector from './StockSelectorDropdown';
import { getAsync, postAsync } from '../utils/utils';
import { useCookies } from 'react-cookie';
import { isAHoliday } from '@18f/us-federal-holidays';
import dayjs from 'dayjs';
import { useEffect } from 'react';



function ButtonField(props) {
    const {
        setOpen,
        label,
        id,
        disabled,
        InputProps: { ref } = {},
        inputProps: { 'aria-label': ariaLabel } = {},
    } = props;
    const theme = useTheme();
    const colors = tokens(theme.palette.mode);

    return (
        <Button
            variant="outlined"
            id={id}
            disabled={disabled}
            ref={ref}
            fullWidth
            aria-label={ariaLabel}
            onClick={() => setOpen?.((prev) => !prev)}
            sx={{ color: colors.grey[100], borderColor: colors.grey[500], marginBottom: "10px", marginTop: "10px" }}
        >
            {label ? `Buy date: ${label}` : 'Choose Buy Date'}
        </Button>
    );
}

function ButtonDatePicker(props) {
    const yesterday = dayjs().subtract(1, 'day');
    const [open, setOpen] = useState(false);
    const theme = useTheme();
    const colors = tokens(theme.palette.mode);

    return (
        <DatePicker
            defaultValue={yesterday}
            slots={{ field: ButtonField, ...props.slots }}
            slotProps={{ field: { setOpen } }}
            {...props}
            open={open}
            onClose={() => setOpen(false)}
            onOpen={() => setOpen(true)}
            sx={{
                '&.MuiButton-outlined': {
                    // Apply your button outline styles here
                    borderColor: colors.greenAccent[400], // Change the border color
                }
            }}
        />
    );
}

export default function AddStocks({ portfolioId }) {
    const theme = useTheme();
    const colors = tokens(theme.palette.mode);
    const [cookie] = useCookies()

    // Initialize state variables for form fields
    const [stockQuantity, setStockQuantity] = useState(0);
    const [stockPrice, setStockPrice] = useState(0);
    const [date, setDate] = useState(null);
    const [chosenStock, setChosenStock] = useState(null);
    const [loading, setLoading] = useState(false);
    const [priceLoading, setPriceLoading] = useState(false);
    const [error, setError] = useState("");
    const [totalPrice, setTotalPrice] = useState(0);
    const [alert, setAlert] = useState({ open: false, type: '', message: '' });

    const showAlert = (type, message) => {
        setAlert({ open: true, type, message });
    };

    const closeAlert = () => {
        setAlert({ open: false, type: '', message: '' });
    };

    const [open, setOpen] = useState(false);

    const handleClickOpen = () => {
        setOpen(true);
    };

    const handleClose = () => {
        setOpen(false);
        setDate(null);
        setStockQuantity(0);
        setChosenStock(null);
        setStockPrice(0);
        setError("");
        setLoading(false);
        setTotalPrice(0);
    };

    const handleStockChange = async (newValue) => {
        setChosenStock(newValue ? newValue : null);
        if (newValue != null) {
            handleStockPriceChange(newValue, date);
        }
    };
    const handleStockPriceChange = async (stock, date) => {
        setError("");
        if (date == null || stock == null) {
            setStockPrice(0);
            return;
        }
        else {
            setLoading(true);
            setPriceLoading(true);
            var dateChosen = date.format('YYYY-MM-DD');
            if (isAHoliday(new Date(dateChosen))) {
                setError("Stock market is closed on this date. Please choose another date.");
                setDate(null);
                setStockPrice(0);
            }
            const response = await getAsync(`stocks/priceAtDate?symbol=${stock.code}&date=${dateChosen}`, cookie.accessToken);
            const data = await response.json();
            if (data.price === undefined) {
                data.price = 0;
                setError("Failed to receive stock price. Please try a different stock")
            }
            setStockPrice(data.price);
            setLoading(false);
            setPriceLoading(false);
        }
    }

    useEffect(() => {
        setTotalPrice(stockPrice * stockQuantity);
        if (stockQuantity <= 0) {
            setError("Quantity must be greater than 0");
        }
        else {
            setError("");
        }
    }, [stockPrice, stockQuantity]);

    const handleAddClick = async () => {
        setLoading(true);
        const data = {
            "portfolioId": portfolioId,
            "symbol": chosenStock.code,
            "buyPrice": stockPrice,
            "quantity": stockQuantity,
            "buyDate": date.format('YYYY-MM-DD')
        }
        try {
            const response = await postAsync(`portfolioStocks/${portfolioId}`, data, cookie.accessToken);
            if (response.ok) {
                setLoading(false);
                showAlert('success', 'Stock added successfully!');
                handleClose();
            }
            else {
                throw response;
            }
        }
        catch (err) {
            handleClose();
            err.json().then(errorDetails => {
                const error_message = errorDetails.details?.split(":")[1];
                showAlert('error', "Error:" + error_message);
            }).catch(jsonError => {
                showAlert('error', "An error occurred");
            });
        }
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
            <Button
                sx={{
                    backgroundColor: colors.blueAccent[700],
                    color: colors.grey[100],
                    fontSize: "14px",
                    fontWeight: "bold",
                    padding: "10px 20px",
                }}
                onClick={handleClickOpen}
            >
                <AddCircleOutlineIcon sx={{ mr: "10px" }} />
                Add Stocks
            </Button>
            <Dialog open={open} onClose={handleClose}>
                <DialogTitle
                    sx={{
                        color: colors.greenAccent[600],
                        backgroundColor: colors.primary[400],
                        fontSize: "22px",
                        fontWeight: "bold",
                        textAlign: "center"
                    }}
                >
                    Add A Stock To This Portfolio
                </DialogTitle>
                <DialogContent
                    sx={{ backgroundColor: colors.primary[400] }}>
                    <Box
                        sx={{ width: '100%', marginTop: "10px", borderRadius: "5px" }}
                    >
                        <StockSelector
                            chosenStock={chosenStock}
                            handleStockChange={handleStockChange}
                        />
                    </Box>
                    <LocalizationProvider dateAdapter={AdapterDayjs}>
                        <ButtonDatePicker
                            disableFuture
                            label={date == null ? null : date.format('DD-MM-YYYY')}
                            value={date}
                            shouldDisableDate={(day) => {
                                // Check if the day is a weekend
                                const isWeekend = [6, 0].includes(day.day()); // 6 = Saturday, 0 = Sunday
                                // Check if the day is today
                                const today = day.isSame(dayjs(), 'day');
                                // Check if the day is public holiday
                                const holiday = isAHoliday(new Date(day.format('YYYY-MM-DD')));
                                // Return true if either is true
                                return isWeekend || today || holiday;
                            }}
                            onChange={(newValue) => { setDate(newValue); handleStockPriceChange(chosenStock, newValue) }}
                        />
                    </LocalizationProvider>
                    <TextField
                        margin="dense"
                        id="price"
                        InputProps={
                            { readOnly: true }
                        }
                        placeholder="e.g. 20"
                        fullWidth
                        value={priceLoading ? 'Loading...' : 'Stock price: $' + stockPrice}
                        sx={{
                            color: colors.grey[100],
                            '& .MuiOutlinedInput-root': {
                                // Apply your input styles here
                                border: `2px solid colors.greenAccent[400]`, // Change the border color
                            },
                            '& .MuiInputLabel-root': {
                                // Apply your label styles here
                                color: colors.grey[100], // Change the label color
                            },

                        }}
                    />

                    <TextField
                        autoFocus
                        margin="dense"
                        id="quantity"
                        label="Stock Quantity"
                        placeholder="e.g. 5"
                        type="number"
                        fullWidth
                        sx={{
                            color: colors.grey[100],
                            '& .MuiOutlinedInput-root': {
                                // Apply your input styles here
                                border: `2px solid colors.greenAccent[400]`, // Change the border color
                            },
                            '& .MuiInputLabel-root': {
                                // Apply your label styles here
                                color: colors.grey[100], // Change the label color
                            },

                        }}
                        value={stockQuantity}
                        onChange={(e) =>
                            setStockQuantity(e.target.value)}
                    />
                    <Box display="flex" justifyContent="space-between" mt="5px">
                        <Typography sx={{ color: colors.redAccent[500], fontSize: "12px" }}>{error}</Typography>
                        <Typography sx={{ color: colors.grey[100], fontSize: "12px" }}>Total Price: ${totalPrice.toFixed(2)}</Typography>
                    </Box>

                </DialogContent>
                <DialogActions sx={{ backgroundColor: colors.primary[400], paddingBottom: "20px", paddingRight: "20px" }}>
                    <Button onClick={handleClose} sx={{ color: colors.grey[300], fontWeight: "bold" }}>Cancel</Button>
                    <Button type="submit" sx={{ backgroundColor: colors.blueAccent[700], color: colors.grey[100], fontWeight: "bold" }} disabled={stockPrice === 0 || chosenStock == null || date == null || stockQuantity <= 0 || stockQuantity % 1 !== 0} onClick={handleAddClick}>{loading ? 'Loading...' : 'Add'}</Button>
                </DialogActions>
            </Dialog>
        </div>
    );
}