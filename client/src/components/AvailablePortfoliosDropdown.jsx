import React, { useState, useEffect } from 'react';
import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import { useCookies } from 'react-cookie';
import { getAsync } from '../utils/utils';
import { useAuth } from '../context/AuthContext';

const useUserID = () => {
    const [cookie] = useCookies();
    const [userId, setUserId] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchData = async () => {
            try {
                const response = await getAsync(`users/user?email=${cookie.email}`, cookie.accessToken);

                if (!response.ok) {
                    throw new Error('Network response was not ok');
                }

                const data = await response.json();

                if (data && data.id) {
                    setUserId(data.id);
                }

            } catch (err) {
                console.error('There was an error fetching the user details:', err);
                setError(err);
            }
        };

        fetchData();

    }, [cookie]);

    return userId; // Only returning userId, but you can also return error if you need to handle it elsewhere
};

const PortfolioSelector = ({ chosenPortfolio, handlePortfolioChange }) => {
    const { userData } = useAuth();
    const userId = userData.id
    const [cookie] = useCookies();
    const [portfolios, setPortfolios] = useState([]);
    const [loading, setLoading] = useState(true); // Added loading state to indicate data fetching

    useEffect(() => {
        if (!userId) return; // If userId is not set yet, do not fetch portfolios

        const fetchData = async () => {
            setLoading(true); // Begin loading
            try {
                const response = await getAsync(`portfolios/user/${userId}`, cookie.accessToken);

                if (!response.ok) {
                    throw new Error('Network response was not ok');
                }

                const data = await response.json();
                setPortfolios(data);
                // Set the chosen portfolio to the first one if not already set
                if (!chosenPortfolio && data.length > 0) {
                    handlePortfolioChange(data[0]);
                }
            } catch (err) {
                console.error('There was an error fetching the portfolios:', err);
            } finally {
                setLoading(false); // Finish loading whether there was an error or not
            }
        };

        fetchData();
    }, [userId]); // useEffect dependency array

    return chosenPortfolio.portfolioId !== undefined && (
        <Autocomplete
            id="portfolio-dropdown"
            options={portfolios}
            getOptionLabel={(option) => option.name}
            fullWidth
            value={chosenPortfolio} // Set the selected value here
            onChange={(event, newValue) => handlePortfolioChange(newValue)}
            isOptionEqualToValue={(option, value) => option.portfolioId === value.portfolioId}
            renderInput={(params) => (
                <TextField
                    {...params}
                    label="Choose a Portfolio"
                    variant="outlined"
                    // Display a loading indicator or placeholder if still loading
                    placeholder={loading ? "Loading..." : "Select a Portfolio"}
                />
            )}
            // Disable the input if there are no portfolios or still loading
            disabled={loading || portfolios.length === 0}
        />
    );
}

export default PortfolioSelector;
