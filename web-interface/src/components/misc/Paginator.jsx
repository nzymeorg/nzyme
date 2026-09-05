import React, {useEffect} from "react";
import PaginatorPageSelector from "./PaginatorPageSelector";

function Paginator(props) {

  const page = props.page;
  const setPage = props.setPage;
  const itemCount = props.itemCount;
  const perPage = props.perPage;

  const pageCount = Math.ceil(itemCount/perPage);

  const onPageClick = function(e, page) {
    e.preventDefault();
    setPage(page);
  }

  return (
      <nav>

        <ul className="pagination justify-content-center mb-0">
          <li className={"page-item " + (page === 1 ? "disabled" : "")}>
            <a className="page-link" href="#" onClick={(e) => onPageClick(e, 1)}>
              &laquo; First
            </a>
          </li>

          <PaginatorPageSelector page={page} toPage={page-2} totalPages={pageCount} onPageClick={onPageClick} />
          <PaginatorPageSelector page={page} toPage={page-1} totalPages={pageCount} onPageClick={onPageClick} />
          <PaginatorPageSelector page={page} toPage={page} totalPages={pageCount} onPageClick={onPageClick} />
          <PaginatorPageSelector page={page} toPage={page+1} totalPages={pageCount} onPageClick={onPageClick} />
          <PaginatorPageSelector page={page} toPage={page+2} totalPages={pageCount} onPageClick={onPageClick} />

          <li className={"page-item " + (page === pageCount ? "disabled" : "")}>
            <a className="page-link" href="#" onClick={(e) => onPageClick(e, pageCount)}>
              Last &raquo;
            </a>
          </li>
        </ul>

      </nav>
  )

}

export default Paginator;